package br.com.vanep.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocationNameNormalizerTest {

  @ParameterizedTest
  @CsvSource({
    "Taguatinga, taguatinga",
    "Águas Claras, aguas claras",
    "São Paulo, sao paulo",
    "Brasília, brasilia",
    "Ceilândia, ceilandia",
    "Itapetininga, itapetininga",
    "Vila Gomes Cardim, vila gomes cardim",
  })
  void removesAccentsAndLowercases(String input, String expected) {
    assertThat(LocationNameNormalizer.normalize(input)).isEqualTo(expected);
  }

  @Test
  void collapsesSurroundingAndRepeatedWhitespace() {
    assertThat(LocationNameNormalizer.normalize("  QNL   5  ")).isEqualTo("qnl 5");
  }

  @Test
  void treatsCedillaAsPlainLetter() {
    assertThat(LocationNameNormalizer.normalize("Conceição")).isEqualTo("conceicao");
  }

  @Test
  void makesDivergentSpellingsOfTheSameRegionCollide() {
    assertThat(LocationNameNormalizer.normalize("Águas Claras"))
        .isEqualTo(LocationNameNormalizer.normalize("AGUAS CLARAS"));
  }

  @Test
  void keepsDistinctRegionsDistinct() {
    assertThat(LocationNameNormalizer.normalize("Taguatinga"))
        .isNotEqualTo(LocationNameNormalizer.normalize("Taguatinga Norte"));
  }

  @Test
  void rejectsNullName() {
    assertThatThrownBy(() -> LocationNameNormalizer.normalize(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankName() {
    assertThatThrownBy(() -> LocationNameNormalizer.normalize("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
