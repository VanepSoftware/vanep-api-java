package br.com.vanep.auth.enums;

public enum AuthErrorCode {
  VALIDATION_ERROR("validation_error"),
  EMAIL_DUPLICATE("email_duplicate"),
  DOCUMENT_DUPLICATE("document_duplicate"),
  INVALID_CODE("invalid_code"),
  INVALID_SIGNUP_TICKET("invalid_signup_ticket");

  private final String value;

  AuthErrorCode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
