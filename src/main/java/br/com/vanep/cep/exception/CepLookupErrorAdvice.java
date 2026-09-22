package br.com.vanep.cep.exception;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CepLookupErrorAdvice {
  private final MessageSource messages;

  public CepLookupErrorAdvice(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(ViaCepNotFoundException.class)
  public ProblemDetail handleNotFound(ViaCepNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "cep.not_found");
  }

  @ExceptionHandler(ViaCepLookupException.class)
  public ProblemDetail handleLookupFailure(ViaCepLookupException exception) {
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "cep.lookup_failed");
  }

  private ProblemDetail problem(HttpStatus status, String key) {
    return ProblemDetail.forStatusAndDetail(
        status, messages.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
