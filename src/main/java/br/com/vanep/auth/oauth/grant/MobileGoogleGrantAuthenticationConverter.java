package br.com.vanep.auth.oauth.grant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

public final class MobileGoogleGrantAuthenticationConverter implements AuthenticationConverter {

  private static final String ID_TOKEN = "id_token";

  @Override
  public Authentication convert(HttpServletRequest request) {
    if (!MobileAuthorizationGrantTypes.GOOGLE
        .getValue()
        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
      return null;
    }
    String idToken = request.getParameter(ID_TOKEN);
    if (!StringUtils.hasText(idToken)) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST));
    }
    return new MobileGoogleGrantAuthenticationToken(
        SecurityContextHolder.getContext().getAuthentication(), idToken);
  }
}
