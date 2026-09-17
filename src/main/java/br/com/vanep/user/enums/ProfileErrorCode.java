package br.com.vanep.user.enums;

/**
 * Machine-readable profile-edit error codes (lowercase snake_case). Clients map {@code code} (+
 * {@code field}) to local copy; server {@code message} is fallback/log only.
 */
public enum ProfileErrorCode {
  COOLDOWN("cooldown"),
  EMAIL_DUPLICATE("email_duplicate"),
  FIELD_NULL("field_null"),
  PHONE_BLANK("phone_blank"),
  EMAIL_SAME("email_same"),
  EMAIL_INVALID("email_invalid"),
  EMAIL_REQUIRED("email_required"),
  NAME_TOO_LONG("name_too_long"),
  PHONE_TOO_LONG("phone_too_long"),
  EMAIL_TOO_LONG("email_too_long");

  private final String value;

  ProfileErrorCode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
