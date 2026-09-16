package br.com.vanep.auth.exception;

/** Every failed code submission answers the same way, so nothing can be probed through it. */
public class InvalidAuthCodeException extends RuntimeException {

  public InvalidAuthCodeException() {
    super("auth.error.invalid_code");
  }
}
