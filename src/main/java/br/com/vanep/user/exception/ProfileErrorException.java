package br.com.vanep.user.exception;

import br.com.vanep.user.enums.ProfileErrorCode;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public abstract class ProfileErrorException extends RuntimeException {

  private final HttpStatus status;
  private final ProfileErrorCode code;
  private final String field;
  private final Instant retryAfter;

  protected ProfileErrorException(
      String message, HttpStatus status, ProfileErrorCode code, String field, Instant retryAfter) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
    this.retryAfter = retryAfter;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public ProfileErrorCode getCode() {
    return code;
  }

  public String getField() {
    return field;
  }

  public Instant getRetryAfter() {
    return retryAfter;
  }
}
