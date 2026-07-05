package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionEnumTest {

  @Test
  void everyValueIsLowercaseVerbUnderscoreResourceInEnglish() {
    Arrays.stream(PermissionEnum.values())
        .forEach(
            permission ->
                assertThat(permission.value())
                    .matches("^[a-z]+_[a-z_]+$")
                    .doesNotContain(":")
                    .isEqualTo(permission.value().toLowerCase()));
  }

  @Test
  void crudForRolesReturnsFiveExpectedPermissions() {
    assertThat(PermissionEnum.crudFor("roles"))
        .containsExactlyInAnyOrder(
            "list_roles", "show_role", "create_role", "update_role", "delete_role");
  }
}
