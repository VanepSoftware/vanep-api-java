package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PepperedArgon2PasswordEncoderTest {

  private final PasswordEncoder encoder = new PepperedArgon2PasswordEncoder("unit-test-pepper");

  @Test
  void encodesAndMatchesCorrectPassword() {
    String hash = encoder.encode("s3cr3t!");

    assertThat(hash).isNotBlank().isNotEqualTo("s3cr3t!");
    assertThat(encoder.matches("s3cr3t!", hash)).isTrue();
  }

  @Test
  void doesNotMatchWrongPassword() {
    String hash = encoder.encode("s3cr3t!");

    assertThat(encoder.matches("wrong", hash)).isFalse();
  }

  @Test
  void differentPepperDoesNotMatch() {
    String hash = encoder.encode("s3cr3t!");
    PasswordEncoder other = new PepperedArgon2PasswordEncoder("another-pepper");

    assertThat(other.matches("s3cr3t!", hash)).isFalse();
  }

  @Test
  void matchesReturnsFalseForBlankHash() {
    assertThat(encoder.matches("s3cr3t!", null)).isFalse();
    assertThat(encoder.matches("s3cr3t!", "")).isFalse();
  }

  @Test
  void requiresPepper() {
    assertThatThrownBy(() -> new PepperedArgon2PasswordEncoder(" "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VANEP_PASSWORD_PEPPER");
  }
}
