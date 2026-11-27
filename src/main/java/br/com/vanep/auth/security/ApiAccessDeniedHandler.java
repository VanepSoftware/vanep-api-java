package br.com.vanep.auth.security;

import br.com.vanep.shared.error.ApiProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

  private final BearerTokenAccessDeniedHandler bearerDelegate =
      new BearerTokenAccessDeniedHandler();
  private final ApiProblemResponseWriter problemWriter;

  public ApiAccessDeniedHandler(ApiProblemResponseWriter problemWriter) {
    this.problemWriter = problemWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    bearerDelegate.handle(request, response, accessDeniedException);
    problemWriter.write(request, response, HttpStatus.FORBIDDEN, "auth.forbidden");
  }
}
