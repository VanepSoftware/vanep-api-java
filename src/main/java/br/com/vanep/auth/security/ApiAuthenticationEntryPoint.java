package br.com.vanep.auth.security;

import br.com.vanep.shared.error.ApiProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final BearerTokenAuthenticationEntryPoint bearerDelegate =
      new BearerTokenAuthenticationEntryPoint();
  private final ApiProblemResponseWriter problemWriter;

  public ApiAuthenticationEntryPoint(ApiProblemResponseWriter problemWriter) {
    this.problemWriter = problemWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    bearerDelegate.commence(request, response, authException);
    problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
  }
}
