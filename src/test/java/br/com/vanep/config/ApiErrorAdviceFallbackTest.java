package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

class ApiErrorAdviceFallbackTest {

  private ApiErrorAdvice advice;
  private WebRequest webRequest;

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    advice = new ApiErrorAdvice(new ValidationProblemDetailsFactory(), messages);
    webRequest = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/test"));
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void handlesOptimisticLockWithUnknownClassNameAsGenericConflict() {
    ResponseEntity<Object> response =
        advice.handleOptimisticLockingFailure(
            new ObjectOptimisticLockingFailureException("com.example.UnknownEntity", 1L),
            webRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getProperties()).containsEntry("code", "request.conflict");
  }

  @Test
  void mapsSpecificSpringMvcExceptionsToContractCodes() throws Exception {
    MethodParameter param = mock(MethodParameter.class);
    List<Exception> exceptions =
        List.of(
            new MissingPathVariableException("token", param),
            new TypeMismatchException("abc", Integer.class),
            new ServletRequestBindingException("missing"),
            mock(HandlerMethodValidationException.class),
            new MissingServletRequestPartException("file"),
            new NoHandlerFoundException("GET", "/api/missing", new HttpHeaders()),
            new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)));
    List<String> expectedCodes =
        List.of(
            "request.parameter_invalid",
            "request.parameter_invalid",
            "request.parameter_invalid",
            "request.parameter_invalid",
            "request.parameter_invalid",
            "request.route_not_found",
            "request.media_type_not_acceptable");

    for (int i = 0; i < exceptions.size(); i++) {
      ResponseEntity<Object> response =
          advice.handleExceptionInternal(
              exceptions.get(i),
              ProblemDetail.forStatus(HttpStatus.BAD_REQUEST),
              new HttpHeaders(),
              HttpStatus.BAD_REQUEST,
              webRequest);
      ProblemDetail body = (ProblemDetail) response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.getProperties()).containsEntry("code", expectedCodes.get(i));
    }
  }

  @ParameterizedTest
  @CsvSource({
    "400, request.parameter_invalid",
    "401, auth.unauthenticated",
    "403, auth.forbidden",
    "404, request.route_not_found",
    "405, request.method_not_allowed",
    "409, request.conflict",
    "413, request.payload_too_large",
    "415, request.media_type_unsupported",
    "422, request.unprocessable",
    "429, request.rate_limited",
    "418, request.error",
    "502, internal.error"
  })
  void fallsBackToContractCodeForAnyHttpStatus(int statusCode, String expectedCode) {
    HttpStatus status = HttpStatus.valueOf(statusCode);
    ResponseEntity<Object> response =
        advice.handleExceptionInternal(
            new RuntimeException("generic"),
            ProblemDetail.forStatus(status),
            new HttpHeaders(),
            status,
            webRequest);

    ProblemDetail body = (ProblemDetail) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getProperties()).containsEntry("code", expectedCode);
  }
}
