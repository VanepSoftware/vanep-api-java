package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PepperedArgon2PasswordEncoderTest {

  private static final String TOKEN_A = "user-token-alpha";
  private static final String TOKEN_B = "user-token-beta";

  private final PepperedArgon2PasswordEncoder encoder =
      new PepperedArgon2PasswordEncoder("unit-test-pepper");

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
    PepperedArgon2PasswordEncoder other = new PepperedArgon2PasswordEncoder("another-pepper");

    assertThat(other.matches("s3cr3t!", hash)).isFalse();
  }

  @Test
  void boundHashMatchesOnlyTheUserItWasCreatedFor() {
    String hash = encoder.encode("s3cr3t!", TOKEN_A);

    assertThat(hash).doesNotContain(TOKEN_A);
    assertThat(encoder.requiresUpgrade(hash)).isFalse();
    assertThat(encoder.matches("s3cr3t!", hash, TOKEN_A)).isTrue();
    assertThat(encoder.matches("wrong", hash, TOKEN_A)).isFalse();
    assertThat(encoder.matches("s3cr3t!", hash, TOKEN_B)).isFalse();
    assertThat(encoder.matches("s3cr3t!", hash)).isFalse();
  }

  @Test
  void legacyHashStillMatchesAndRequiresUpgrade() {
    String legacy = encoder.encode("s3cr3t!");

    assertThat(encoder.requiresUpgrade(legacy)).isTrue();
    assertThat(encoder.matches("s3cr3t!", legacy, TOKEN_A)).isTrue();
    assertThat(encoder.matches("wrong", legacy, TOKEN_A)).isFalse();

    String upgraded = encoder.encode("s3cr3t!", TOKEN_A);
    assertThat(encoder.requiresUpgrade(upgraded)).isFalse();
    assertThat(encoder.matches("s3cr3t!", upgraded, TOKEN_A)).isTrue();
    assertThat(encoder.matches("s3cr3t!", upgraded, TOKEN_B)).isFalse();
  }

  @Test
  void boundMatchRejectsAMissingUserToken() {
    String hash = encoder.encode("s3cr3t!", TOKEN_A);

    assertThat(encoder.matches("s3cr3t!", hash, null)).isFalse();
    assertThat(encoder.matches("s3cr3t!", hash, " ")).isFalse();
    assertThat(encoder.matches(null, hash, TOKEN_A)).isFalse();
    assertThat(encoder.requiresUpgrade(null)).isFalse();
    assertThat(encoder.requiresUpgrade(" ")).isFalse();
  }

  @Test
  void encodeForUserRequiresAPasswordAndAToken() {
    assertThatThrownBy(() -> encoder.encode(null, TOKEN_A))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> encoder.encode("s3cr3t!", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void differentPepperDoesNotMatchABoundHash() {
    String hash = encoder.encode("s3cr3t!", TOKEN_A);
    PepperedArgon2PasswordEncoder other = new PepperedArgon2PasswordEncoder("another-pepper");

    assertThat(other.matches("s3cr3t!", hash, TOKEN_A)).isFalse();
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
