package br.com.vanep.auth.dto;

import java.util.List;

/**
 * Error envelope of the public auth API. It carries one entry per invalid field, which the profile
 * envelope cannot: that one names a single field.
 */
public record AuthErrorResponseDTO(String code, String message, List<FieldErrorDTO> errors) {

  public record FieldErrorDTO(String field, String message) {}

  public static AuthErrorResponseDTO of(String code, String message) {
    return new AuthErrorResponseDTO(code, message, List.of());
  }
}
