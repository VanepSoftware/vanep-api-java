package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

  @Test
  void passesWithStrongSecrets() {
    assertThatCode(
            () ->
                new ProductionSecretsValidator(
                        "strong-remember-key", "strong-pepper", "PEM-DATA", "web-client-secret")
                    .afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDefaultRememberMeKey() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator(
                        "vanep-remember-me-change-me", "strong-pepper", "PEM", "web-client-secret")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDefaultPepper() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator(
                        "k", "dev-pepper-please-change", "PEM", "web-client-secret")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankJwk() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "p", "  ", "web-client-secret")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankWebClientSecret() {
    assertThatThrownBy(
            () -> new ProductionSecretsValidator("k", "p", "PEM", "  ").afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }
}
