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
  DELETE_CLIENT("delete_client"),
  LIST_VEHICLES("list_vehicles"),
  SHOW_VEHICLE("show_vehicle"),
  CREATE_VEHICLE("create_vehicle"),
  UPDATE_VEHICLE("update_vehicle"),
  DELETE_VEHICLE("delete_vehicle"),
  RESTORE_VEHICLE("restore_vehicle"),
  LIST_DEPENDENTS("list_dependents"),
  SHOW_DEPENDENT("show_dependent"),
  CREATE_DEPENDENT("create_dependent"),
  UPDATE_DEPENDENT("update_dependent"),
  DELETE_DEPENDENT("delete_dependent"),
  LIST_DRIVERS("list_drivers"),
  SHOW_DRIVER("show_driver"),
  CREATE_DRIVER("create_driver"),
  UPDATE_DRIVER("update_driver"),
  DELETE_DRIVER("delete_driver"),
  RESTORE_DRIVER("restore_driver"),
  LIST_SCHOOLS("list_schools"),
  SHOW_SCHOOL("show_school"),
  CREATE_SCHOOL("create_school"),
  UPDATE_SCHOOL("update_school"),
  DELETE_SCHOOL("delete_school"),
  RESTORE_SCHOOL("restore_school"),
  LIST_STATES("list_states"),
  SHOW_STATE("show_state"),
  LIST_CITIES("list_cities"),
  SHOW_CITY("show_city"),
  CREATE_CITY("create_city"),
  UPDATE_CITY("update_city"),
  LIST_ADDRESSES("list_addresses"),
  SHOW_ADDRESS("show_address"),
  CREATE_ADDRESS("create_address"),
  UPDATE_ADDRESS("update_address"),
  DELETE_ADDRESS("delete_address"),
  DELETE_CITY("delete_city"),
  LIST_ASSISTANTS("list_assistants"),
  SHOW_ASSISTANT("show_assistant"),
  UPDATE_ASSISTANT("update_assistant"),
  PAUSE_ASSISTANT("pause_assistant"),
  RESUME_ASSISTANT("resume_assistant"),
  REVOKE_ASSISTANT("revoke_assistant"),
  CREATE_ASSISTANT_INVITE("create_assistant_invite"),
  CANCEL_ASSISTANT_INVITE("cancel_assistant_invite"),
  LIST_COUNTRIES("list_countries"),
  SHOW_COUNTRY("show_country"),
  CREATE_COUNTRY("create_country"),
  UPDATE_COUNTRY("update_country"),
  DELETE_COUNTRY("delete_country"),
  RESTORE_COUNTRY("restore_country");

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
