package br.com.vanep.shared.error;

import java.io.Serial;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An API error whose {@code code} is its message key, so clients decide by the code and only show
 * the {@code detail}, which the error advice translates from the same key.
 */
public class ApiProblemException extends ErrorResponseException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  public ApiProblemException(HttpStatusCode status, String messageKey, Object... arguments) {
    this(status, messageKey, null, arguments);
  }

  protected ApiProblemException(
      HttpStatusCode status, String key, Throwable cause, Object... args) {
    super(status, problemWithCode(status, key), cause, key, args);
    this.code = key;
  }

  private static ProblemDetail problemWithCode(HttpStatusCode status, String key) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setProperty("code", key);
    return problem;
  }

  public String getCode() {
    return code;
  }

  public String getMessageKey() {
    return code;
  }

  public ApiProblemException with(String name, Object value) {
    if (value != null) {
      getBody().setProperty(name, value);
    }
    return this;
  }

  public ApiProblemException withProperty(String name, Object value) {
    return with(name, value);
  }

  public ApiProblemException withAll(Map<String, ?> extras) {
    if (extras != null) {
      extras.forEach(this::with);
    }
    return this;
  }

  @Override
  public String getMessage() {
    return getStatusCode() + " " + code;
  }

  public static ApiProblemException of(HttpStatusCode status, String key, Object... args) {
    return new ApiProblemException(status, key, null, args);
  }

  public static ApiProblemException of(
      HttpStatusCode status, String key, Throwable cause, Object... args) {
    return new ApiProblemException(status, key, cause, args);
  }

  public static ApiProblemException badRequest(String key, Object... args) {
    return of(HttpStatus.BAD_REQUEST, key, args);
  }

  public static ApiProblemException unauthorized(String key, Object... args) {
    return of(HttpStatus.UNAUTHORIZED, key, args);
  }

  public static ApiProblemException forbidden(String key, Object... args) {
    return of(HttpStatus.FORBIDDEN, key, args);
  }

  public static ApiProblemException notFound(String key, Object... args) {
    return of(HttpStatus.NOT_FOUND, key, args);
  }

  public static ApiProblemException conflict(String key, Object... args) {
    return of(HttpStatus.CONFLICT, key, args);
  }

  public static ApiProblemException unprocessable(String key, Object... args) {
    return of(HttpStatus.UNPROCESSABLE_CONTENT, key, args);
  }

  public static ApiProblemException tooManyRequests(String key, Object... args) {
    return of(HttpStatus.TOO_MANY_REQUESTS, key, args);
  }

  public static ApiProblemException serviceUnavailable(String key, Object... args) {
    return of(HttpStatus.SERVICE_UNAVAILABLE, key, args);
  }
}
