package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vanep.shared.error.ApiProblemResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ApiAuthenticationEntryPointTest {

  @Test
  void setsWwwAuthenticateHeaderAndWritesUnauthenticatedProblemDetail() throws Exception {
    ApiProblemResponseWriter problemWriter = mock(ApiProblemResponseWriter.class);
    ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(problemWriter);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients/me");
    request.setRequestURI("/api/clients/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(
        request, response, new InsufficientAuthenticationException("Authentication required"));

    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("Bearer");
    verify(problemWriter).write(request, response, HttpStatus.UNAUTHORIZED, "auth.unauthenticated");
  }
}
