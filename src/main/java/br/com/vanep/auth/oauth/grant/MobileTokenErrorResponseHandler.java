package br.com.vanep.auth.oauth.grant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Standard OAuth error body for every failure, plus the sign-up ticket for the one error that needs
 * it. Keeping the extra fields here is what lets the grant stay a plain token request.
 */
public final class MobileTokenErrorResponseHandler implements AuthenticationFailureHandler {

  private final HttpMessageConverter<OAuth2Error> errorResponseConverter =
      new OAuth2ErrorHttpMessageConverter();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (exception instanceof RegistrationRequiredException registrationRequired) {
      writeRegistrationRequired(response, registrationRequired);
      return;
    }
    ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
    httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      errorResponseConverter.write(oauth2Exception.getError(), null, httpResponse);
    }
  }

  private void writeRegistrationRequired(
      HttpServletResponse response, RegistrationRequiredException exception) throws IOException {
    Map<String, String> body = new LinkedHashMap<>();
    body.put(OAuth2ParameterNames.ERROR, exception.getError().getErrorCode());
    body.put(OAuth2ParameterNames.ERROR_DESCRIPTION, exception.getError().getDescription());
    body.put("signup_ticket", exception.getSignupTicket());
    body.put("email", exception.getEmail());
    body.put("name", exception.getName());

    response.setStatus(HttpStatus.BAD_REQUEST.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
