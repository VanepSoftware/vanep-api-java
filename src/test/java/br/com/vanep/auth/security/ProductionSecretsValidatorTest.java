package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

  private static final String STRONG_CHAT_KEY =
      "9f3ca1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2a1b2";

  @Test
  void passesWithStrongSecrets() {
    assertThatCode(
            () ->
                new ProductionSecretsValidator(
                        "strong-remember-key",
                        "strong-pepper",
                        "PEM-DATA",
                        "web-client-secret",
                        STRONG_CHAT_KEY)
                    .afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDefaultRememberMeKey() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator(
                        "vanep-remember-me-change-me",
                        "strong-pepper",
                        "PEM",
                        "web-client-secret",
                        STRONG_CHAT_KEY)
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDefaultPepper() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator(
                        "k",
                        "dev-pepper-please-change",
                        "PEM",
                        "web-client-secret",
                        STRONG_CHAT_KEY)
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankJwk() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "p", "  ", "web-client-secret", STRONG_CHAT_KEY)
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankWebClientSecret() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "p", "PEM", "  ", STRONG_CHAT_KEY)
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankChatEncryptionKey() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "p", "PEM", "web-client-secret", " ")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDevelopmentChatEncryptionKey() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "p", "PEM", "web-client-secret", "0".repeat(64))
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }
}
