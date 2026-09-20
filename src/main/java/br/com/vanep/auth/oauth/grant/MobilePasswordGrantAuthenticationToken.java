package br.com.vanep.auth.oauth.grant;

import java.io.Serial;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

@Getter
public class MobilePasswordGrantAuthenticationToken
    extends OAuth2AuthorizationGrantAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final String username;
  private final String password;

  public MobilePasswordGrantAuthenticationToken(
      Authentication clientPrincipal, String username, String password) {
    super(MobileAuthorizationGrantTypes.PASSWORD, clientPrincipal, Map.of());
    this.username = username;
    this.password = password;
  }
}
