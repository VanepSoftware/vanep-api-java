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

public final class MobilePasswordGrantAuthenticationConverter implements AuthenticationConverter {

  // OAuth2ParameterNames dropped these with the OAuth 2.1 removal of the password grant, but the
  // parameter names on the wire stay the ones every client library already knows.
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";

  @Override
  public Authentication convert(HttpServletRequest request) {
    if (!MobileAuthorizationGrantTypes.PASSWORD
        .getValue()
        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
      return null;
    }
    String username = request.getParameter(USERNAME);
    String password = request.getParameter(PASSWORD);
    if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST));
    }
    return new MobilePasswordGrantAuthenticationToken(
        SecurityContextHolder.getContext().getAuthentication(), username, password);
  }
}
