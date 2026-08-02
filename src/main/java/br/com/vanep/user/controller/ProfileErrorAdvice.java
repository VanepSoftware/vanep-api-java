package br.com.vanep.user.controller;

import br.com.vanep.user.dto.ProfileErrorResponseDTO;
import br.com.vanep.user.exception.ProfileErrorException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProfileErrorAdvice {

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
}
