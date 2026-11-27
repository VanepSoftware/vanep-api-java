package br.com.vanep.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AssistantLinkServiceTest {

  @Mock private AssistantRepository assistantRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserService userService;

  private AssistantLinkService service;

  @BeforeEach
  void setUp() {
    service =
        new AssistantLinkService(
            assistantRepository,
            driverRepository,
            userRepository,
            userService,
            new AssistantMapper());
  }

  @Test
  void pauseResumeAndRevokeEnforceExpectedAssistantStates() {
    DriverModel driver = driver(10L, "driver@vanep.com");
    AssistantModel active = assistant(20L, "ast-1", AssistantStatus.ACTIVE, driver);
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(driver.getUser()));
    when(driverRepository.findByUserId(driver.getUser().getId())).thenReturn(Optional.of(driver));
    when(assistantRepository.findByToken("ast-1")).thenReturn(Optional.of(active));

    service.pause("driver@vanep.com", "ast-1");
    assertThat(active.getStatus()).isEqualTo(AssistantStatus.INACTIVE);

    assertThatThrownBy(() -> service.pause("driver@vanep.com", "ast-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("assistant.link.not_active");
            });

    service.resume("driver@vanep.com", "ast-1");
    assertThat(active.getStatus()).isEqualTo(AssistantStatus.ACTIVE);

    assertThatThrownBy(() -> service.resume("driver@vanep.com", "ast-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getCode()).isEqualTo("assistant.link.not_inactive");
            });

    service.revokeByDriver("driver@vanep.com", "ast-1");
    assertThat(active.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(active.getDriver()).isNull();
    assertThat(active.getActivatedAt()).isNull();

    active.setDriver(driver);
    assertThatThrownBy(() -> service.revokeByDriver("driver@vanep.com", "ast-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex ->
                assertThat(((ApiProblemException) ex).getCode())
                    .isEqualTo("assistant.link.not_active"));
  }

  @Test
  void revokeSelfAndOwnershipValidationRejectInvalidLinks() {
    DriverModel owner = driver(10L, "owner@vanep.com");
    DriverModel stranger = driver(99L, "stranger@vanep.com");
    AssistantModel linkedToOwner = assistant(20L, "ast-1", AssistantStatus.ACTIVE, owner);

    when(userRepository.findByEmail("stranger@vanep.com"))
        .thenReturn(Optional.of(stranger.getUser()));
    when(driverRepository.findByUserId(stranger.getUser().getId()))
        .thenReturn(Optional.of(stranger));
    when(assistantRepository.findByToken("ast-1")).thenReturn(Optional.of(linkedToOwner));

    assertThatThrownBy(() -> service.pause("stranger@vanep.com", "ast-1"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(problem.getCode()).isEqualTo("assistant.link.forbidden");
            });

    UserModel assistantUser = linkedToOwner.getUser();
    when(userService.requireByTokenAndType("u-ast", UserType.ASSISTANT)).thenReturn(assistantUser);
    when(assistantRepository.findByUserId(assistantUser.getId()))
        .thenReturn(Optional.of(linkedToOwner));

    service.revokeSelf("u-ast");
    assertThat(linkedToOwner.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    verify(assistantRepository).save(linkedToOwner);

    assertThatThrownBy(() -> service.revokeSelf("u-ast"))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex ->
                assertThat(((ApiProblemException) ex).getCode())
                    .isEqualTo("assistant.link.not_active"));
  }

  @Test
  void listForDriverIncludesOnlyActiveAndInactiveAssistants() {
    DriverModel driver = driver(10L, "driver@vanep.com");
    AssistantModel active = assistant(1L, "a1", AssistantStatus.ACTIVE, driver);
    AssistantModel unlinked = assistant(2L, "a2", AssistantStatus.UNLINKED, driver);
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(driver.getUser()));
    when(driverRepository.findByUserId(driver.getUser().getId())).thenReturn(Optional.of(driver));
    when(assistantRepository.findByDriverId(10L)).thenReturn(List.of(active, unlinked));

    assertThat(service.listForDriver("driver@vanep.com"))
        .extracting(item -> item.token())
        .containsExactly("a1");
  }

  private static DriverModel driver(Long id, String email) {
    UserModel user = new UserModel();
    user.setId(id + 100);
    user.setEmail(email);
    user.setName("Driver " + id);

    DriverModel driver = new DriverModel();
    driver.setId(id);
    driver.setUser(user);
    return driver;
  }

  private static AssistantModel assistant(
      Long id, String token, AssistantStatus status, DriverModel driver) {
    UserModel user = new UserModel();
    user.setId(id + 200);
    user.setEmail(token + "@vanep.com");
    user.setName("Assistant " + token);

    AssistantModel model = new AssistantModel();
    model.setId(id);
    model.setToken(token);
    model.setUser(user);
    model.setDriver(driver);
    model.setStatus(status);
    model.setActivatedAt(Instant.now());
    return model;
  }
}
