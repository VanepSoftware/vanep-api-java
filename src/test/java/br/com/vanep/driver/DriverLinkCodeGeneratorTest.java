package br.com.vanep.driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class DriverLinkCodeGeneratorTest {

  private static final String FORBIDDEN_CHARS = "0O1I";

  @RepeatedTest(20)
  void generatesSixCharacterCodeWithoutAmbiguousCharacters() {
    String code = DriverLinkCodeGenerator.generate();

    assertThat(code).hasSize(6);
    for (char c : code.toCharArray()) {
      assertThat(FORBIDDEN_CHARS).doesNotContain(String.valueOf(c));
      assertThat(DriverLinkCodeGeneratorTest.ALPHABET).contains(String.valueOf(c));
    }
  }

  @Test
  void generatesDifferentCodes() {
    assertThat(DriverLinkCodeGenerator.generate()).isNotEqualTo(DriverLinkCodeGenerator.generate());
  }

  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
}
