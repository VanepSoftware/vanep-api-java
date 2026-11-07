package br.com.vanep.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

  private final ValidationProblemDetailsFactory validationProblems;

  public ApiErrorAdvice(ValidationProblemDetailsFactory validationProblems) {
    this.validationProblems = validationProblems;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = validationProblems.create(exception);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }
}
