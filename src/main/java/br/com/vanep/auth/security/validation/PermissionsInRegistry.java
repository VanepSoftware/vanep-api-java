package br.com.vanep.auth.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PermissionsInRegistryValidator.class)
public @interface PermissionsInRegistry {

  String message() default "{role_permission.permissions.invalid}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
