package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PermissionRegistryTest {

  @Test
  void allReturnsExactlyEnumValuesWithoutDuplicates() {
    var expected =
        Arrays.stream(PermissionEnum.values())
            .map(PermissionEnum::value)
            .collect(Collectors.toSet());

    var result = PermissionRegistry.all();

    assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(result).hasSize(PermissionEnum.values().length);
  }

  @Test
  void containsReturnsTrueForKnownPermission() {
    assertThat(PermissionRegistry.contains("list_roles")).isTrue();
  }

  @Test
  void containsReturnsFalseForUnknownPermission() {
    assertThat(PermissionRegistry.contains("fly_to_moon")).isFalse();
  }
}
