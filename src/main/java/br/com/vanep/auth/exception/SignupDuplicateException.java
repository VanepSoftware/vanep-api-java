package br.com.vanep.auth.exception;

import lombok.Getter;

@Getter
public class SignupDuplicateException extends RuntimeException {

  private final String field;
  private final String messageKey;

  public SignupDuplicateException(String field, String messageKey) {
    super(messageKey);
    this.field = field;
    this.messageKey = messageKey;
  }
}
