package br.com.vanep.user.exception;

import java.time.Instant;

public abstract class ProfileConflictException extends RuntimeException {

  private final String code;
  private final String field;
  private final Instant retryAfter;

  protected ProfileConflictException(
      String message, String code, String field, Instant retryAfter) {
    super(message);
    this.code = code;
    this.field = field;
    this.retryAfter = retryAfter;
  }

  public String getCode() {
    return code;
  }

  public String getField() {
    return field;
  }

  public Instant getRetryAfter() {
    return retryAfter;
  }
}
