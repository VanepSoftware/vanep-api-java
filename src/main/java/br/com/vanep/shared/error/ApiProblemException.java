package br.com.vanep.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An API error whose {@code code} is its message key, so clients decide by the code and only show
 * the {@code detail}, which the error advice translates from the same key.
 */
public class ApiProblemException extends ErrorResponseException {

  private final String messageKey;

  public ApiProblemException(HttpStatusCode status, String messageKey, Object... arguments) {
    super(status, ProblemDetail.forStatus(status), null, messageKey, arguments);
    this.messageKey = messageKey;
    getBody().setProperty("code", messageKey);
  }

  public static ApiProblemException badRequest(String messageKey, Object... arguments) {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, messageKey, arguments);
  }

  public static ApiProblemException notFound(String messageKey, Object... arguments) {
    return new ApiProblemException(HttpStatus.NOT_FOUND, messageKey, arguments);
  }

  public static ApiProblemException conflict(String messageKey, Object... arguments) {
    return new ApiProblemException(HttpStatus.CONFLICT, messageKey, arguments);
  }

  public static ApiProblemException unprocessable(String messageKey, Object... arguments) {
    return new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, messageKey, arguments);
  }

  public ApiProblemException withProperty(String name, Object value) {
    getBody().setProperty(name, value);
    return this;
  }

  public String getMessageKey() {
    return messageKey;
  }

  @Override
  public String getMessage() {
    return getStatusCode() + " " + messageKey;
  }
}
