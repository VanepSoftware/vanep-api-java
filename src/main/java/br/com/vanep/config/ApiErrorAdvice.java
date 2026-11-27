package br.com.vanep.config;

import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.shared.error.ApiProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.ClassUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

  private final ValidationProblemDetailsFactory validationProblems;

  @Autowired
  public ApiErrorAdvice(
      ValidationProblemDetailsFactory validationProblems, MessageSource messages) {
    this.validationProblems = validationProblems;
    setMessageSource(messages);
  }

  ApiErrorAdvice(ValidationProblemDetailsFactory validationProblems) {
    this.validationProblems = validationProblems;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Object> handleOptimisticLockingFailure(
      ObjectOptimisticLockingFailureException exception, WebRequest request) {
    ApiProblemException conflict =
        ApiProblemException.conflict(
            isDocumentSubmission(exception) ? "document.review.stale" : "request.conflict");
    return handleErrorResponseException(
        conflict, new HttpHeaders(), conflict.getStatusCode(), request);
  }

  // Hibernate names the entity, not the class, when it reports a stale row.
  static boolean isDocumentSubmission(ObjectOptimisticLockingFailureException exception) {
    String entityName = exception.getPersistentClassName();
    return entityName != null
        && ClassUtils.isPresent(entityName, null)
        && DocumentSubmission.class.isAssignableFrom(ClassUtils.resolveClassName(entityName, null));
  }

  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ApiProblemException tooLarge = MediaFileService.tooLarge();
    return handleErrorResponseException(tooLarge, headers, tooLarge.getStatusCode(), request);
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

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Object> handleAccessDenied(
      AccessDeniedException exception, WebRequest request) {
    return handleProblem(exception, HttpStatus.FORBIDDEN, "auth.forbidden", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
    log.error("Unhandled API error", exception);
    return handleProblem(exception, HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(exception, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      ensureProblemCode(exception, statusCode, problem);
    }
    return response;
  }

  private ResponseEntity<Object> handleProblem(
      Exception exception, HttpStatus status, String key, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, resolveMessage(key, status.getReasonPhrase()));
    problem.setProperty("code", key);
    return handleExceptionInternal(exception, problem, new HttpHeaders(), status, request);
  }

  private void ensureProblemCode(
      Exception exception, HttpStatusCode statusCode, ProblemDetail problem) {
    Object existing = problem.getProperties() == null ? null : problem.getProperties().get("code");
    if (existing instanceof String text && !text.isBlank()) {
      return;
    }
    String code = resolveGenericCode(exception, statusCode);
    problem.setProperty("code", code);
    if (!(exception instanceof ResponseStatusException)) {
      String localized = resolveMessage(code, problem.getDetail());
      if (localized != null) {
        problem.setDetail(localized);
      }
    }
  }

  private String resolveGenericCode(Exception exception, HttpStatusCode statusCode) {
    return switch (exception) {
      case HttpMessageNotReadableException ignored -> "request.body_unreadable";
      case MissingServletRequestParameterException ignored -> "request.parameter_invalid";
      case MissingPathVariableException ignored -> "request.parameter_invalid";
      case MissingRequestValueException ignored -> "request.parameter_invalid";
      case TypeMismatchException ignored -> "request.parameter_invalid";
      case ServletRequestBindingException ignored -> "request.parameter_invalid";
      case HandlerMethodValidationException ignored -> "request.parameter_invalid";
      case MissingServletRequestPartException ignored -> "request.parameter_invalid";
      case NoResourceFoundException ignored -> "request.route_not_found";
      case NoHandlerFoundException ignored -> "request.route_not_found";
      case HttpRequestMethodNotSupportedException ignored -> "request.method_not_allowed";
      case HttpMediaTypeNotSupportedException ignored -> "request.media_type_unsupported";
      case HttpMediaTypeNotAcceptableException ignored -> "request.media_type_not_acceptable";
      case MaxUploadSizeExceededException ignored -> "request.payload_too_large";
      case AccessDeniedException ignored -> "auth.forbidden";
      default -> fallbackCodeForStatus(statusCode);
    };
  }

  private String fallbackCodeForStatus(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 400 -> "request.parameter_invalid";
      case 401 -> "auth.unauthenticated";
      case 403 -> "auth.forbidden";
      case 404 -> "request.route_not_found";
      case 405 -> "request.method_not_allowed";
      case 409 -> "request.conflict";
      case 413 -> "request.payload_too_large";
      case 415 -> "request.media_type_unsupported";
      case 422 -> "request.unprocessable";
      case 429 -> "request.rate_limited";
      default -> statusCode.is5xxServerError() ? "internal.error" : "request.error";
    };
  }

  private String resolveMessage(String key, String fallback) {
    MessageSource messages = getMessageSource();
    if (messages == null) {
      return fallback;
    }
    return messages.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
  }
}
