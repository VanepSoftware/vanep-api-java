package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

  @Test
  void passesWithStrongSecrets() {
    assertThatCode(
            () ->
                new ProductionSecretsValidator("strong-remember-key", "strong-pepper", "PEM-DATA")
                    .afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDefaultRememberMeKey() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator(
                        "vanep-remember-me-change-me", "strong-pepper", "PEM")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsDefaultPepper() {
    assertThatThrownBy(
            () ->
                new ProductionSecretsValidator("k", "dev-pepper-please-change", "PEM")
                    .afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsBlankJwk() {
    assertThatThrownBy(() -> new ProductionSecretsValidator("k", "p", "  ").afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class);
  }
}
