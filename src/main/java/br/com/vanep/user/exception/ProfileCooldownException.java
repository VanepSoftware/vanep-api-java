package br.com.vanep.user.exception;

import br.com.vanep.user.enums.ProfileErrorCode;
import java.io.Serial;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public class ProfileCooldownException extends ProfileErrorException {

  @Serial private static final long serialVersionUID = 1L;

  public ProfileCooldownException(String message, String field, Instant retryAfter) {
    super(message, HttpStatus.CONFLICT, ProfileErrorCode.COOLDOWN, field, retryAfter);
  }
}
