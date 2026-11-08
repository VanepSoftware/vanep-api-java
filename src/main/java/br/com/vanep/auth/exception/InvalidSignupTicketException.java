package br.com.vanep.auth.exception;

import java.io.Serial;

/** Unknown, expired or already used ticket: the same answer for all three. */
public class InvalidSignupTicketException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidSignupTicketException() {
    super("auth.error.invalid_signup_ticket");
  }
}
