package br.com.vanep.user.controller;

import br.com.vanep.user.dto.ProfileConflictResponseDTO;
import br.com.vanep.user.exception.ProfileConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProfileConflictAdvice {

  @ExceptionHandler(ProfileConflictException.class)
  public ResponseEntity<ProfileConflictResponseDTO> handleProfileConflict(
      ProfileConflictException exception) {
    ProfileConflictResponseDTO body =
        new ProfileConflictResponseDTO(
            exception.getMessage(),
            exception.getCode(),
            exception.getField(),
            exception.getRetryAfter());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }
}
