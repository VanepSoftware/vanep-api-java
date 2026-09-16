package br.com.vanep.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  @ParameterizedTest
  @ValueSource(strings = {"Secret@1", "Ábc!de", "SENHA#1", "Minha senha."})
  void acceptsPasswordsWithUppercaseAndSpecialCharacter(String password) {
    assertThat(PasswordPolicy.hasUppercaseLetter(password)).isTrue();
    assertThat(PasswordPolicy.hasSpecialCharacter(password)).isTrue();
  }

  @Test
  void detectsMissingUppercase() {
    assertThat(PasswordPolicy.hasUppercaseLetter("secret@1")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Secret12", "Senha com espaco"})
  void lettersDigitsAndSpacesAreNotSpecialCharacters(String password) {
    assertThat(PasswordPolicy.hasSpecialCharacter(password)).isFalse();
  }
}
