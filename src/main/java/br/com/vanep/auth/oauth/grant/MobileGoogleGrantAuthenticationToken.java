package br.com.vanep.auth.oauth.grant;

import java.io.Serial;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

@Getter
public class MobileGoogleGrantAuthenticationToken
    extends OAuth2AuthorizationGrantAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final String idToken;

  public MobileGoogleGrantAuthenticationToken(Authentication clientPrincipal, String idToken) {
    super(MobileAuthorizationGrantTypes.GOOGLE, clientPrincipal, Map.of());
    this.idToken = idToken;
  }
}
