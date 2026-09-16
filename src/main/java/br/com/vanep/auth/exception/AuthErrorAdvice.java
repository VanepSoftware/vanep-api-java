package br.com.vanep.auth.exception;

import br.com.vanep.auth.api.SignupApiController;
import br.com.vanep.auth.dto.AuthErrorResponseDTO;
import br.com.vanep.auth.enums.AuthErrorCode;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to the auth API controllers and ordered ahead of the global profile advice, which also
 * handles {@code MethodArgumentNotValidException}.
 */
@RestControllerAdvice(assignableTypes = {SignupApiController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthErrorAdvice {

  private final MessageSource messages;

  public AuthErrorAdvice(MessageSource messages) {
    this.messages = messages;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<AuthErrorResponseDTO> handleValidation(
      MethodArgumentNotValidException exception) {
    List<AuthErrorResponseDTO.FieldErrorDTO> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error -> new AuthErrorResponseDTO.FieldErrorDTO(error.getField(), messageOf(error)))
            .toList();
    return ResponseEntity.badRequest()
        .body(
            new AuthErrorResponseDTO(
                AuthErrorCode.VALIDATION_ERROR.value(), message("auth.error.validation"), errors));
  }

  @ExceptionHandler(SignupDuplicateException.class)
  public ResponseEntity<AuthErrorResponseDTO> handleDuplicate(SignupDuplicateException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            AuthErrorResponseDTO.of(
                exception.getCode().value(), message(exception.getMessageKey())));
  }

  private String messageOf(FieldError error) {
    return error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField();
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
