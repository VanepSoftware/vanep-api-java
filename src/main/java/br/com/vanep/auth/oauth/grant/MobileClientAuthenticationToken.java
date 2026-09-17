package br.com.vanep.auth.oauth.grant;

import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Deliberately not an {@code OAuth2ClientAuthenticationToken}: the server's own client-auth
 * providers accept that type, and a {@code ProviderManager} keeps trying providers after a failure
 * and reports the last error. Our own type keeps this request ours alone, so rejecting it answers
 * {@code invalid_client} instead of whatever the PKCE provider would have said.
 */
public class MobileClientAuthenticationToken extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final String clientId;

  public MobileClientAuthenticationToken(String clientId) {
    super(List.of());
    this.clientId = clientId;
  }

  @Override
  public Object getPrincipal() {
    return clientId;
  }

  @Override
  public Object getCredentials() {
    return null;
  }
}
