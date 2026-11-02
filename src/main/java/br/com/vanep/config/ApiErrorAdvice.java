package br.com.vanep.config;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

  private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotBlank", "NotEmpty", "NotNull");

  private static final Comparator<ValidationError> VALIDATION_ERROR_ORDER =
      Comparator.comparingInt(ValidationError::priority)
          .thenComparing(ValidationError::field, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(ValidationError::message);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationError> errors =
        exception.getBindingResult().getAllErrors().stream()
            .map(ApiErrorAdvice::toValidationError)
            .sorted(VALIDATION_ERROR_ORDER)
            .toList();
    ValidationError selected = errors.getFirst();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, selected.message());
    if (selected.field() != null) {
      problem.setProperty("field", selected.field());
    }
    problem.setProperty("errors", errors.stream().map(ValidationError::response).toList());
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  public ResponseEntity<Object> handleValidationException(
      MethodArgumentNotValidException exception, WebRequest request) {
    return handleMethodArgumentNotValid(
        exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
  }

  private static ValidationError toValidationError(ObjectError error) {
    String field = error instanceof FieldError fieldError ? fieldError.getField() : null;
    String message =
        error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getCode();
    return new ValidationError(field, message, error.getCode());
  }

  private record ValidationError(String field, String message, String constraint) {

    int priority() {
      return REQUIRED_CONSTRAINTS.contains(constraint) ? 0 : 1;
    }

    Map<String, String> response() {
      Map<String, String> response = new LinkedHashMap<>();
      if (field != null) {
        response.put("field", field);
      }
      response.put("message", message);
      return response;
    }
  }
}
