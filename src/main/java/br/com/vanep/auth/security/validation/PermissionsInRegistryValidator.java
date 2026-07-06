package br.com.vanep.auth.security.validation;

import br.com.vanep.auth.security.PermissionRegistry;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class PermissionsInRegistryValidator
    implements ConstraintValidator<PermissionsInRegistry, List<String>> {

  @Override
  public boolean isValid(List<String> permissions, ConstraintValidatorContext context) {
    if (permissions == null) {
      return true;
    }
    return permissions.stream().allMatch(PermissionRegistry::contains);
  }
}
