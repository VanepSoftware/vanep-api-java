package br.com.vanep.user.exception;

import br.com.vanep.user.enums.ProfileErrorCode;
import org.springframework.http.HttpStatus;

/** Structured HTTP 400 for profile-edit validation (same envelope as 409). */
public class ProfileBadRequestException extends ProfileErrorException {

  private ProfileBadRequestException(String message, ProfileErrorCode code, String field) {
    super(message, HttpStatus.BAD_REQUEST, code, field, null);
  }

  public static ProfileBadRequestException fieldNull(String message, String field) {
    return new ProfileBadRequestException(message, ProfileErrorCode.FIELD_NULL, field);
  }

  public static ProfileBadRequestException phoneBlank(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.PHONE_BLANK, "phone");
  }

  public static ProfileBadRequestException emailSame(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.EMAIL_SAME, "email");
  }

  public static ProfileBadRequestException emailInvalid(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.EMAIL_INVALID, "email");
  }

  public static ProfileBadRequestException emailRequired(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.EMAIL_REQUIRED, "email");
  }

  public static ProfileBadRequestException nameTooLong(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.NAME_TOO_LONG, "name");
  }

  public static ProfileBadRequestException phoneTooLong(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.PHONE_TOO_LONG, "phone");
  }

  public static ProfileBadRequestException emailTooLong(String message) {
    return new ProfileBadRequestException(message, ProfileErrorCode.EMAIL_TOO_LONG, "email");
  }
}
