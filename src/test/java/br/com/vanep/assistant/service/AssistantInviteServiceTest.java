package br.com.vanep.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.dto.AssistantInviteCreateRequestDTO;
import br.com.vanep.assistant.dto.AssistantInviteResponseDTO;
import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssistantInviteServiceTest {

  @Mock private AssistantInviteRepository inviteRepository;
  @Mock private AssistantRepository assistantRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private MailService mail;
  @Mock private AssistantMapper mapper;
  @Mock private MessageSource messages;

  private AssistantInviteService service;

  private UserModel driverUser;
  private DriverModel driver;
  private UserModel assistantUser;
  private AssistantModel assistant;

  @BeforeEach
  void setUp() {
    service =
        new AssistantInviteService(
            inviteRepository,
            assistantRepository,
            driverRepository,
            userRepository,
            mail,
            mapper,
            messages,
            72);

    lenient()
        .when(messages.getMessage(any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    driverUser = new UserModel();
    driverUser.setId(1L);
    driverUser.setEmail("driver@vanep.com");
    driverUser.setName("Driver");
    driverUser.setType(UserType.DRIVER);

    driver = new DriverModel();
    driver.setId(10L);
    driver.setUser(driverUser);

    assistantUser = new UserModel();
    assistantUser.setId(2L);
    assistantUser.setEmail("assistant@vanep.com");
    assistantUser.setName("Assistant");
    assistantUser.setType(UserType.ASSISTANT);

    assistant = new AssistantModel();
    assistant.setId(20L);
    assistant.setUser(assistantUser);
    assistant.setStatus(AssistantStatus.UNLINKED);
  }

  @Test
  void createInviteSendsEmailAndSetsPending() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(userRepository.findByEmail("assistant@vanep.com")).thenReturn(Optional.of(assistantUser));
    when(assistantRepository.findByUserId(2L)).thenReturn(Optional.of(assistant));
    when(inviteRepository.findByAssistantIdAndStatus(20L, AssistantInviteStatus.PENDING))
        .thenReturn(Optional.empty());
    when(inviteRepository.save(any(AssistantInviteModel.class)))
        .thenAnswer(
            invocation -> {
              AssistantInviteModel invite = invocation.getArgument(0);
              invite.setToken("invite-token");
              invite.setCreatedAt(Instant.now());
              invite.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
              return invite;
            });
    when(mapper.toInviteResponse(any()))
        .thenReturn(
            new AssistantInviteResponseDTO(
                "invite-token",
                "assistant@vanep.com",
                AssistantInviteStatus.PENDING,
                Instant.now().plus(72, ChronoUnit.HOURS),
                Instant.now()));

    service.createInvite(
        "driver@vanep.com", new AssistantInviteCreateRequestDTO("assistant@vanep.com"));

    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.PENDING);
    verify(mail)
        .send(
            eq("assistant@vanep.com"),
            eq("assistant.invite.email.subject"),
            eq("email/assistant-invite"),
            anyMap());
  }

  @Test
  void expireIfStaleMarksInviteExpiredAndRevertsAssistant() {
    AssistantInviteModel invite = new AssistantInviteModel();
    invite.setStatus(AssistantInviteStatus.PENDING);
    invite.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
    invite.setAssistant(assistant);
    assistant.setStatus(AssistantStatus.PENDING);

    service.expireIfStale(invite);

    assertThat(invite.getStatus()).isEqualTo(AssistantInviteStatus.EXPIRED);
    assertThat(invite.getRespondedAt()).isNotNull();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    verify(inviteRepository).save(invite);
    verify(assistantRepository).save(assistant);
  }

  @Test
  void createInviteThrowsConflictForWrongUserType() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    assistantUser.setType(UserType.CLIENT);
    when(userRepository.findByEmail("client@vanep.com")).thenReturn(Optional.of(assistantUser));

    assertThatThrownBy(
            () ->
                service.createInvite(
                    "driver@vanep.com", new AssistantInviteCreateRequestDTO("client@vanep.com")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void resendCancelsPreviousPendingInviteFromSameDriver() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(userRepository.findByEmail("assistant@vanep.com")).thenReturn(Optional.of(assistantUser));
    when(assistantRepository.findByUserId(2L)).thenReturn(Optional.of(assistant));

    AssistantInviteModel previous = new AssistantInviteModel();
    previous.setDriver(driver);
    previous.setAssistant(assistant);
    previous.setStatus(AssistantInviteStatus.PENDING);
    previous.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

    assistant.setStatus(AssistantStatus.PENDING);
    when(inviteRepository.findByAssistantIdAndStatus(20L, AssistantInviteStatus.PENDING))
        .thenReturn(Optional.of(previous));
    when(inviteRepository.save(any(AssistantInviteModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createInvite(
        "driver@vanep.com", new AssistantInviteCreateRequestDTO("assistant@vanep.com"));

    ArgumentCaptor<AssistantInviteModel> captor =
        ArgumentCaptor.forClass(AssistantInviteModel.class);
    verify(inviteRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    assertThat(
            captor.getAllValues().stream()
                .anyMatch(invite -> invite.getStatus() == AssistantInviteStatus.CANCELLED))
        .isTrue();
  }
}
