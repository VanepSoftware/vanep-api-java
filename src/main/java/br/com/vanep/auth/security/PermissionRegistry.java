package br.com.vanep.auth.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class PermissionRegistry {

  private PermissionRegistry() {}

  public static Set<String> all() {
    return Arrays.stream(PermissionEnum.values())
        .map(PermissionEnum::value)
        .collect(Collectors.toUnmodifiableSet());
  }

  public static boolean contains(String permission) {
    return all().contains(permission);
  }
}
