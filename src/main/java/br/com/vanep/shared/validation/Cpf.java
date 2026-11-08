package br.com.vanep.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CpfConstraintValidator.class)
public @interface Cpf {

  String message() default "{auth.signup.document.invalid}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
