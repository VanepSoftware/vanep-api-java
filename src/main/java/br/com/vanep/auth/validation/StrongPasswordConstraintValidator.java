package br.com.vanep.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordConstraintValidator
    implements ConstraintValidator<StrongPassword, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      // @NotBlank owns empty messages
      return true;
    }
    boolean hasUppercase = PasswordPolicy.hasUppercaseLetter(value);
    boolean hasSpecial = PasswordPolicy.hasSpecialCharacter(value);
    if (hasUppercase && hasSpecial) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    if (!hasUppercase) {
      context
          .buildConstraintViolationWithTemplate("{auth.signup.password.uppercase}")
          .addConstraintViolation();
    }
    if (!hasSpecial) {
      context
          .buildConstraintViolationWithTemplate("{auth.signup.password.special}")
          .addConstraintViolation();
    }
    return false;
  }
}
