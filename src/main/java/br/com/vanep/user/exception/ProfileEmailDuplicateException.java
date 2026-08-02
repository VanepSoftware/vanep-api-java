package br.com.vanep.user.exception;

public class ProfileEmailDuplicateException extends ProfileConflictException {

  public static final String CODE = "email_duplicate";
  public static final String FIELD = "email";

  public ProfileEmailDuplicateException(String message) {
    super(message, CODE, FIELD, null);
  }
}
