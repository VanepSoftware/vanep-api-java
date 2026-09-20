package br.com.vanep.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reports one violation per missing requirement of {@link PasswordPolicy}. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordConstraintValidator.class)
public @interface StrongPassword {

  String message() default "{auth.signup.password.uppercase}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
