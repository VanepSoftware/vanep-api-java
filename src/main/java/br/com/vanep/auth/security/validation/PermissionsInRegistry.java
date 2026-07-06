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

  String message() default "Permissões inválidas: contém valores fora do catálogo permitido.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
