package br.com.vanep.user.exception;

import br.com.vanep.user.enums.ProfileErrorCode;
import org.springframework.http.HttpStatus;

public class ProfileEmailDuplicateException extends ProfileErrorException {

  public static final String FIELD = "email";

  public ProfileEmailDuplicateException(String message) {
    super(message, HttpStatus.CONFLICT, ProfileErrorCode.EMAIL_DUPLICATE, FIELD, null);
  }
}
