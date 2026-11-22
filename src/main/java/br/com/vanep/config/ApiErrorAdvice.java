package br.com.vanep.config;

import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.shared.error.ApiProblemException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

  private final ValidationProblemDetailsFactory validationProblems;

  public ApiErrorAdvice(ValidationProblemDetailsFactory validationProblems) {
    this.validationProblems = validationProblems;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public @Nullable ResponseEntity<Object> handleOptimisticLockingFailure(
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
  protected @Nullable ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ApiProblemException tooLarge = MediaFileService.tooLarge();
    return handleErrorResponseException(tooLarge, headers, tooLarge.getStatusCode(), request);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = validationProblems.create(exception);
    return handleExceptionInternal(exception, problem, headers, status, request);
  }
}
