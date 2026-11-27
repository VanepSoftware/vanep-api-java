package br.com.vanep.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

class ApiProblemResponseWriterTest {

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void resolvesLocaleUsingConfiguredLocaleResolverEvenBeforeDispatcherServlet() throws Exception {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    ApiProblemResponseWriter writer =
        new ApiProblemResponseWriter(
            messageSource(), JsonMapper.builder().build(), new FixedLocaleResolver(PT_BR));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations/me");
    request.setRequestURI("/api/conversations/me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(request, response, HttpStatus.UNAUTHORIZED, "auth.unauthenticated");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var body = JsonMapper.builder().build().readTree(response.getContentAsString());
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("code").asString()).isEqualTo("auth.unauthenticated");
    assertThat(body.get("detail").asString())
        .isEqualTo("Autenticação necessária. Entre novamente para continuar.");
    assertThat(body.get("instance").asString()).isEqualTo("/api/conversations/me");
  }

  @Test
  void fallsBackToLocaleContextHolderAndOmitsBlankInstance() throws Exception {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    ApiProblemResponseWriter writer = ApiProblemResponseWriter.withDefaults(messageSource());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
    request.setRequestURI("   ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(request, response, HttpStatus.FORBIDDEN, "auth.forbidden");

    assertThat(response.getStatus()).isEqualTo(403);
    var body = JsonMapper.builder().build().readTree(response.getContentAsString());
    assertThat(body.get("code").asString()).isEqualTo("auth.forbidden");
    assertThat(body.get("detail").asString())
        .isEqualTo("You do not have permission to perform this action.");
    assertThat(body.has("instance")).isFalse();
  }

  private static ResourceBundleMessageSource messageSource() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    return messages;
  }
}
