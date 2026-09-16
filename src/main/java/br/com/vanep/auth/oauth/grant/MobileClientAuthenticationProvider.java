package br.com.vanep.auth.oauth.grant;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public final class MobileClientAuthenticationProvider implements AuthenticationProvider {

  private final RegisteredClientRepository registeredClients;
  private final String mobileClientId;

  public MobileClientAuthenticationProvider(
      RegisteredClientRepository registeredClients, String mobileClientId) {
    this.registeredClients = registeredClients;
    this.mobileClientId = mobileClientId;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    MobileClientAuthenticationToken request = (MobileClientAuthenticationToken) authentication;
    String clientId = request.getPrincipal().toString();
    if (!mobileClientId.equals(clientId)) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
    }
    RegisteredClient registeredClient = registeredClients.findByClientId(clientId);
    if (registeredClient == null
        || !registeredClient
            .getClientAuthenticationMethods()
            .contains(ClientAuthenticationMethod.NONE)) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT));
    }
    return new MobileClientAuthenticationToken(registeredClient);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return MobileClientAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
