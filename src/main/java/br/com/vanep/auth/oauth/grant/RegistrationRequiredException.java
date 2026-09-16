package br.com.vanep.auth.oauth.grant;

import java.io.Serial;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * The only token endpoint error that carries extra fields: the app needs the ticket, the e-mail and
 * the name to open the sign-up screen already filled in.
 */
@Getter
public class RegistrationRequiredException extends OAuth2AuthenticationException {

  @Serial private static final long serialVersionUID = 1L;

  public static final String ERROR_CODE = "registration_required";

  private final String signupTicket;
  private final String email;
  private final String name;

  public RegistrationRequiredException(
      String description, String signupTicket, String email, String name) {
    super(new OAuth2Error(ERROR_CODE, description, null));
    this.signupTicket = signupTicket;
    this.email = email;
    this.name = name;
  }
}
