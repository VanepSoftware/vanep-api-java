package br.com.vanep.auth.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vanep.shared.error.ApiProblemResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

class ApiAccessDeniedHandlerTest {

  @Test
  void delegatesBearerHeaderAndWritesForbiddenProblemDetail() throws Exception {
    ApiProblemResponseWriter problemWriter = mock(ApiProblemResponseWriter.class);
    ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(problemWriter);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/drivers/me");
    request.setRequestURI("/api/drivers/me");
    BearerTokenAuthentication auth = mock(BearerTokenAuthentication.class);
    request.setUserPrincipal(auth);
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("Forbidden"));

    verify(problemWriter).write(request, response, HttpStatus.FORBIDDEN, "auth.forbidden");
  }
}
