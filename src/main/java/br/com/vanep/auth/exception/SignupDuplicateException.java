package br.com.vanep.auth.exception;

import br.com.vanep.auth.enums.AuthErrorCode;
import lombok.Getter;

@Getter
public class SignupDuplicateException extends RuntimeException {

  private final String field;
  private final String messageKey;
  private final AuthErrorCode code;

  public SignupDuplicateException(String field, String messageKey, AuthErrorCode code) {
    super(messageKey);
    this.field = field;
    this.messageKey = messageKey;
    this.code = code;
  }
}
