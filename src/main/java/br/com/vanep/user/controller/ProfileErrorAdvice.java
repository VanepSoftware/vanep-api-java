package br.com.vanep.user.controller;

import br.com.vanep.user.dto.ProfileErrorResponseDTO;
import br.com.vanep.user.dto.UserEmailChangeRequestDTO;
import br.com.vanep.user.enums.ProfileErrorCode;
import br.com.vanep.user.exception.ProfileErrorException;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ProfileErrorAdvice extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ProfileErrorException.class)
  public ResponseEntity<ProfileErrorResponseDTO> handleProfileError(
      ProfileErrorException exception) {
    ProfileErrorResponseDTO body =
        new ProfileErrorResponseDTO(
            exception.getMessage(),
            exception.getCode().value(),
            exception.getField(),
            exception.getRetryAfter());
    return ResponseEntity.status(exception.getStatus()).body(body);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (!(ex.getBindingResult().getTarget() instanceof UserEmailChangeRequestDTO)) {
      return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    List<FieldError> emailErrors = ex.getBindingResult().getFieldErrors("email");
    FieldError selected = selectEmailFieldError(emailErrors);
    ProfileErrorCode code = resolveEmailValidationCode(selected);
    String message =
        selected != null && selected.getDefaultMessage() != null
            ? selected.getDefaultMessage()
            : code.value();

    ProfileErrorResponseDTO body =
        new ProfileErrorResponseDTO(message, code.value(), "email", null);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  /**
   * Prefer NotBlank, then Size, then Email when several constraints fail (e.g. a very long string
   * often fails both {@code @Email} and {@code @Size}).
   */
  static FieldError selectEmailFieldError(List<FieldError> emailErrors) {
    if (emailErrors == null || emailErrors.isEmpty()) {
      return null;
    }
    FieldError required = findByCodePrefix(emailErrors, "NotBlank");
    if (required != null) {
      return required;
    }
    FieldError tooLong = findByCodePrefix(emailErrors, "Size");
    if (tooLong != null) {
      return tooLong;
    }
    return emailErrors.getFirst();
  }

  private static FieldError findByCodePrefix(List<FieldError> errors, String prefix) {
    for (FieldError error : errors) {
      if (error.getCodes() == null) {
        continue;
      }
      boolean match =
          Arrays.stream(error.getCodes()).anyMatch(c -> c != null && c.startsWith(prefix));
      if (match) {
        return error;
      }
    }
    return null;
  }

  private static ProfileErrorCode resolveEmailValidationCode(FieldError fieldError) {
    if (fieldError == null || fieldError.getCodes() == null) {
      return ProfileErrorCode.EMAIL_INVALID;
    }
    boolean required =
        Arrays.stream(fieldError.getCodes()).anyMatch(c -> c != null && c.startsWith("NotBlank"));
    if (required) {
      return ProfileErrorCode.EMAIL_REQUIRED;
    }
    boolean tooLong =
        Arrays.stream(fieldError.getCodes()).anyMatch(c -> c != null && c.startsWith("Size"));
    if (tooLong) {
      return ProfileErrorCode.EMAIL_TOO_LONG;
    }
    return ProfileErrorCode.EMAIL_INVALID;
  }
}
