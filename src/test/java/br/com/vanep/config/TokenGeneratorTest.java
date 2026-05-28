package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class TokenGeneratorTest {

  @RepeatedTest(5)
  void generate_returns25CharHex() {
    String token = TokenGenerator.generate();
    assertThat(token).hasSize(25).matches("[0-9a-f]+");
  }
}
