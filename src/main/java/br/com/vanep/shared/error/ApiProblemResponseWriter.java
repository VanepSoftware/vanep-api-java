package br.com.vanep.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiProblemResponseWriter {

  private final MessageSource messages;
  private final JacksonJsonHttpMessageConverter converter;
  private final LocaleResolver localeResolver;

  @Autowired
  public ApiProblemResponseWriter(
      MessageSource messages,
      JsonMapper jsonMapper,
      ObjectProvider<LocaleResolver> localeResolverProvider) {
    this(messages, jsonMapper, localeResolverProvider.getIfAvailable());
  }

  public ApiProblemResponseWriter(MessageSource messages, JsonMapper jsonMapper) {
    this(messages, jsonMapper, (LocaleResolver) null);
  }

  ApiProblemResponseWriter(
      MessageSource messages, JsonMapper jsonMapper, LocaleResolver localeResolver) {
    this.messages = messages;
    this.localeResolver = localeResolver;
    JsonMapper problemMapper =
        jsonMapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    this.converter = new JacksonJsonHttpMessageConverter(problemMapper);
  }

  public static ApiProblemResponseWriter withDefaults(MessageSource messages) {
    return new ApiProblemResponseWriter(messages, JsonMapper.builder().build());
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String key)
      throws IOException {
    Locale locale =
        localeResolver != null
            ? localeResolver.resolveLocale(request)
            : LocaleContextHolder.getLocale();
    String detail = messages.getMessage(key, null, status.getReasonPhrase(), locale);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", key);
    String uri = request.getRequestURI();
    if (uri != null && !uri.isBlank()) {
      problem.setInstance(URI.create(uri));
    }

    response.setStatus(status.value());
    converter.write(
        problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
  }
}
