package br.com.vanep.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.verification.EmailVerificationTokenRepository;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.policy.UserProfileChangePolicy;
import br.com.vanep.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final int COOLDOWN_DAYS = 30;

  @Mock private UserRepository users;
  @Mock private MessageSource messages;
  @Mock private EmailVerificationTokenRepository verificationTokens;

  private UserService service;

  @BeforeEach
  void setUp() {
    service =
        new UserService(
            users, messages, verificationTokens, new UserProfileChangePolicy(COOLDOWN_DAYS));
    lenient()
        .when(
            messages.getMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void requireByTokenReturnsUserWhenFound() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThat(service.requireByToken("uid-1")).isSameAs(user);
  }

  @Test
  void requireByTokenThrows404WhenMissing() {
    when(users.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void requireByTokenAndTypeReturnsUserWhenTypeMatches() {
    UserModel user = userWithToken("uid-1", UserType.DRIVER);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThat(service.requireByTokenAndType("uid-1", UserType.DRIVER)).isSameAs(user);
  }

  @Test
  void requireByTokenAndTypeThrows403WhenTypeMismatch() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.requireByTokenAndType("uid-1", UserType.DRIVER))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
        .isEqualTo(403);
  }

  @Test
  void getMeIncludesBirthDateAndGender() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setBirthDate(LocalDate.of(1990, 5, 15));
    user.setGender(Gender.FEMALE);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.birthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
    assertThat(me.gender()).isEqualTo(Gender.FEMALE);
    assertThat(me.token()).isEqualTo("uid-1");
    assertThat(me.type()).isEqualTo("CLIENT");
  }

  @Test
  void getMeAllowsNullBirthDateAndGender() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.birthDate()).isNull();
    assertThat(me.gender()).isNull();
    assertThat(me.pendingEmail()).isNull();
    assertThat(me.nameChangeAvailableAt()).isNull();
    assertThat(me.phoneChangeAvailableAt()).isNull();
    assertThat(me.emailChangeAvailableAt()).isNull();
  }

  @Test
  void getMeShowsActivePendingEmailWhenOpenTokenExists() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setId(42L);
    user.setPendingEmail("new@vanep.com");
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));
    when(verificationTokens.existsByUserIdAndConsumedAtIsNullAndExpiresAtAfter(
            eq(42L), any(Instant.class)))
        .thenReturn(true);

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.pendingEmail()).isEqualTo("new@vanep.com");
  }

  @Test
  void getMeHidesGhostPendingWhenNoOpenToken() {
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setId(42L);
    user.setPendingEmail("stale@vanep.com");
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));
    when(verificationTokens.existsByUserIdAndConsumedAtIsNullAndExpiresAtAfter(
            eq(42L), any(Instant.class)))
        .thenReturn(false);

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.pendingEmail()).isNull();
  }

  @Test
  void getMeIncludesNameChangeAvailableAtWhileCoolingDown() {
    Instant lastChange = Instant.now().minus(Duration.ofDays(5));
    Instant expectedAvailableAt = lastChange.plus(Duration.ofDays(COOLDOWN_DAYS));
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setLastNameChangeAt(lastChange);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.nameChangeAvailableAt()).isEqualTo(expectedAvailableAt);
    assertThat(me.phoneChangeAvailableAt()).isNull();
    assertThat(me.emailChangeAvailableAt()).isNull();
  }

  @Test
  void getMeNullsAvailableAtWhenCooldownElapsed() {
    Instant lastChange = Instant.now().minus(Duration.ofDays(COOLDOWN_DAYS + 1));
    UserModel user = userWithToken("uid-1", UserType.CLIENT);
    user.setLastNameChangeAt(lastChange);
    user.setLastPhoneChangeAt(lastChange);
    user.setLastEmailChangeAt(lastChange);
    when(users.findByToken("uid-1")).thenReturn(Optional.of(user));

    UserMeResponseDTO me = service.getMe("uid-1");

    assertThat(me.nameChangeAvailableAt()).isNull();
    assertThat(me.phoneChangeAvailableAt()).isNull();
    assertThat(me.emailChangeAvailableAt()).isNull();
  }

  private static UserModel userWithToken(String token, UserType type) {
    UserModel user = new UserModel();
    user.setToken(token);
    user.setType(type);
    user.setName("Test User");
    user.setEmail("test@vanep.com");
    user.setDocument("12345678901");
    user.setPhone("11999999999");
    return user;
  }
}
