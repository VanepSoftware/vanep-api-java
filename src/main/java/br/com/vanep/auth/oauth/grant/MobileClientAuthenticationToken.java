package br.com.vanep.auth.oauth.grant;

import java.io.Serial;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * Marks a client authentication request that {@link MobileClientAuthenticationConverter} accepted,
 * so {@link MobileClientAuthenticationProvider} never sees a PKCE request and cannot authenticate
 * an authorization code without its {@code code_verifier}.
 */
public class MobileClientAuthenticationToken extends OAuth2ClientAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  public MobileClientAuthenticationToken(String clientId) {
    super(clientId, ClientAuthenticationMethod.NONE, null, null);
  }

  public MobileClientAuthenticationToken(RegisteredClient registeredClient) {
    super(registeredClient, ClientAuthenticationMethod.NONE, null);
  }
}
