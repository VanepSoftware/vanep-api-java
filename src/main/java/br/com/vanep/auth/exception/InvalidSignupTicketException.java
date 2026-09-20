package br.com.vanep.auth.exception;

/** Unknown, expired or already used ticket: the same answer for all three. */
public class InvalidSignupTicketException extends RuntimeException {

  public InvalidSignupTicketException() {
    super("auth.error.invalid_signup_ticket");
  }
}
