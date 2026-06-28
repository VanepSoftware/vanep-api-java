package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

  @Test
  void blocksAfterReachingMaxAttempts() {
    LoginAttemptService service = new LoginAttemptService(3, 15);
    assertThat(service.isBlocked("user@vanep.com")).isFalse();
    service.loginFailed("user@vanep.com");
    service.loginFailed("user@vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isFalse();
    service.loginFailed("user@vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isTrue();
  }

  @Test
  void isCaseInsensitiveOnKey() {
    LoginAttemptService service = new LoginAttemptService(1, 15);
    service.loginFailed("User@Vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isTrue();
  }

  @Test
  void successResetsCounter() {
    LoginAttemptService service = new LoginAttemptService(2, 15);
    service.loginFailed("user@vanep.com");
    service.loginFailed("user@vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isTrue();
    service.loginSucceeded("user@vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isFalse();
  }

  @Test
  void expiredWindowUnblocks() {
    LoginAttemptService service = new LoginAttemptService(1, 0);
    service.loginFailed("user@vanep.com");
    assertThat(service.isBlocked("user@vanep.com")).isFalse();
  }

  @Test
  void blankKeyIsIgnored() {
    LoginAttemptService service = new LoginAttemptService(1, 15);
    service.loginFailed(" ");
    assertThat(service.isBlocked(" ")).isFalse();
    assertThat(service.isBlocked(null)).isFalse();
  }
}
