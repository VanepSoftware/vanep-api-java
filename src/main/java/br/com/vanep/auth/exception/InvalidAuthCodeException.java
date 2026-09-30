package br.com.vanep.auth.exception;

import java.io.Serial;

/** Every failed code submission answers the same way, so nothing can be probed through it. */
public class InvalidAuthCodeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidAuthCodeException() {
    super("auth.error.invalid_code");
  }
}
