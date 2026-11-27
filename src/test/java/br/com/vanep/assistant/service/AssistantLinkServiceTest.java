package br.com.vanep.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class AssistantLinkServiceTest {

  private static final Instant NOW = Instant.parse("2027-03-01T01:00:00Z");

  @Mock private AssistantRepository assistants;
  @Mock private DriverRepository drivers;
  @Mock private UserRepository users;
  @Mock private UserService userService;
  @Mock private AssistantMapper mapper;
  @Mock private MessageSource messages;
  @Mock private AssistantInviteRepository invites;

  @Test
  void listsThePendingInvitesStillValidAtTheClockInstant() {
    UserModel user = new UserModel();
    user.setId(1L);
    DriverModel driver = new DriverModel();
    driver.setId(7L);
    when(users.findByEmail("driver@vanep.test")).thenReturn(Optional.of(user));
    when(drivers.findByUserId(1L)).thenReturn(Optional.of(driver));
    AssistantLinkService service =
        new AssistantLinkService(
            assistants,
            drivers,
            users,
            userService,
            mapper,
            messages,
            invites,
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThat(service.listForDriver("driver@vanep.test")).isEmpty();

    verify(invites)
        .findByDriverIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            7L, AssistantInviteStatus.PENDING, NOW);
    verify(assistants).findByDriverId(7L);
  }
}
