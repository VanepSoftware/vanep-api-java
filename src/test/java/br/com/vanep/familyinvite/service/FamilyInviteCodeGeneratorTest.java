package br.com.vanep.familyinvite.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FamilyInviteCodeGeneratorTest {

  private final FamilyInviteCodeGenerator generator = new FamilyInviteCodeGenerator();

  @Test
  void generatesCodesWithoutAmbiguousCharacters() {
    for (int attempt = 0; attempt < 200; attempt++) {
      assertThat(generator.generateCode())
          .hasSize(FamilyInviteCodeGenerator.CODE_LENGTH)
          .doesNotContainAnyWhitespaces()
          .matches("[A-HJKMNP-Z2-9]+");
    }
  }

  @Test
  void generatesDistinctCodes() {
    Set<String> codes = new HashSet<>();
    for (int attempt = 0; attempt < 500; attempt++) {
      codes.add(generator.generateCode());
    }
    assertThat(codes).hasSize(500);
  }
}
