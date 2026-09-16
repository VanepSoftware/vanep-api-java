package br.com.vanep.auth.oauth.grant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Authenticates the first-party mobile app by {@code client_id} alone, which the Spring
 * Authorization Server otherwise only accepts for {@code authorization_code} with PKCE. The client
 * id of a public app is extractable, so this is configuration, not a security barrier: the grants
 * themselves defend with uniform lockout and rate limiting.
 */
public final class MobileClientAuthenticationConverter implements AuthenticationConverter {

  private static final Set<String> SUPPORTED_GRANT_TYPES =
      Set.of(
          MobileAuthorizationGrantTypes.PASSWORD.getValue(),
          MobileAuthorizationGrantTypes.GOOGLE.getValue(),
          AuthorizationGrantType.REFRESH_TOKEN.getValue());

  private final RequestMatcher tokenEndpointMatcher;
  private final RequestMatcher revocationEndpointMatcher;

  public MobileClientAuthenticationConverter(AuthorizationServerSettings settings) {
    this.tokenEndpointMatcher =
        PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, settings.getTokenEndpoint());
    this.revocationEndpointMatcher =
        PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, settings.getTokenRevocationEndpoint());
  }

  /**
   * Recognises the shape of a mobile request and leaves to {@link
   * MobileClientAuthenticationProvider} the decision of which client id may use it, so an unknown
   * client gets the OAuth {@code invalid_client} body instead of the login redirect.
   */
  @Override
  public Authentication convert(HttpServletRequest request) {
    if (!isSupportedTokenRequest(request) && !isRevocationRequest(request)) {
      return null;
    }
    String clientId = soleClientId(request);
    if (clientId == null) {
      return null;
    }
    return new MobileClientAuthenticationToken(clientId);
  }

  private boolean isSupportedTokenRequest(HttpServletRequest request) {
    return tokenEndpointMatcher.matches(request)
        && SUPPORTED_GRANT_TYPES.contains(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
  }

  private boolean isRevocationRequest(HttpServletRequest request) {
    return revocationEndpointMatcher.matches(request)
        && StringUtils.hasText(request.getParameter(OAuth2ParameterNames.TOKEN));
  }

  private String soleClientId(HttpServletRequest request) {
    String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
    boolean authenticatesByClientIdAlone =
        clientIds != null
            && clientIds.length == 1
            && StringUtils.hasText(clientIds[0])
            && !StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))
            && !StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION));
    return authenticatesByClientIdAlone ? clientIds[0] : null;
  }
}
