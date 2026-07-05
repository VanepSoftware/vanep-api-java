package br.com.vanep.auth.security;

import java.util.Set;

public enum PermissionEnum {
  LIST_ROLES("list_roles"),
  SHOW_ROLE("show_role"),
  CREATE_ROLE("create_role"),
  UPDATE_ROLE("update_role"),
  DELETE_ROLE("delete_role"),
  LIST_ROLE_PERMISSIONS("list_role_permissions"),
  SHOW_ROLE_PERMISSION("show_role_permission"),
  CREATE_ROLE_PERMISSION("create_role_permission"),
  UPDATE_ROLE_PERMISSION("update_role_permission"),
  DELETE_ROLE_PERMISSION("delete_role_permission"),
  LIST_CLIENTS("list_clients"),
  SHOW_CLIENT("show_client"),
  CREATE_CLIENT("create_client"),
  UPDATE_CLIENT("update_client"),
  DELETE_CLIENT("delete_client");

  private final String value;

  PermissionEnum(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Set<String> crudFor(String resource) {
    return Set.of(
        "list_" + resource,
        "show_" + resource.substring(0, resource.length() - 1),
        "create_" + resource.substring(0, resource.length() - 1),
        "update_" + resource.substring(0, resource.length() - 1),
        "delete_" + resource.substring(0, resource.length() - 1));
  }
}
