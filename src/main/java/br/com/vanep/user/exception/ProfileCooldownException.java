package br.com.vanep.user.exception;

import java.time.Instant;

public class ProfileCooldownException extends ProfileConflictException {

  public static final String CODE = "cooldown";

  public ProfileCooldownException(String message, String field, Instant retryAfter) {
    super(message, CODE, field, retryAfter);
  }
}
