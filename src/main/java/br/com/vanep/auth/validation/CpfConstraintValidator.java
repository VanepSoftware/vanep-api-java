package br.com.vanep.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfConstraintValidator implements ConstraintValidator<Cpf, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      // @NotBlank owns empty messages
      return true;
    }
    return CpfValidator.isValid(value);
  }
}
