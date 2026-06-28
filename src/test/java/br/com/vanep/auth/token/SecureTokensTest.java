package br.com.vanep.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureTokensTest {

  @Test
  void generateProducesUniqueUrlSafeTokens() {
    String a = SecureTokens.generate();
    String b = SecureTokens.generate();
    assertThat(a).isNotBlank().doesNotContain("+", "/", "=");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void hashIsDeterministicAndHex() {
    String raw = SecureTokens.generate();
    assertThat(SecureTokens.hash(raw))
        .isEqualTo(SecureTokens.hash(raw))
        .hasSize(64)
        .matches("[0-9a-f]+");
  }

  @Test
  void hashDiffersForDifferentInput() {
    assertThat(SecureTokens.hash("a")).isNotEqualTo(SecureTokens.hash("b"));
  }
}
