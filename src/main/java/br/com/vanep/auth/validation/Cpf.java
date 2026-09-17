package br.com.vanep.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CpfConstraintValidator.class)
public @interface Cpf {

  String message() default "{auth.signup.document.invalid}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
