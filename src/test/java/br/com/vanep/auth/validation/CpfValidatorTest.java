package br.com.vanep.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfValidatorTest {

  @Test
  void normalizeStripsNonDigits() {
    assertThat(CpfValidator.normalize("390.533.447-05")).isEqualTo("39053344705");
    assertThat(CpfValidator.normalize(" 390 533 447 05 ")).isEqualTo("39053344705");
  }

  @Test
  void normalizeNullReturnsEmpty() {
    assertThat(CpfValidator.normalize(null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"39053344705", "390.533.447-05", "52998224725", "11144477735"})
  void acceptsValidCpfs(String cpf) {
    assertThat(CpfValidator.isValid(cpf)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "11111111111",
        "00000000000",
        "12345678900",
        "390.533.447-00",
        "123",
        "abcdefghijk"
      })
  void rejectsInvalidCpfs(String cpf) {
    assertThat(CpfValidator.isValid(cpf)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "..."})
  void rejectsBlankOrNonDigitOnly(String cpf) {
    assertThat(CpfValidator.isValid(cpf)).isFalse();
  }
}
