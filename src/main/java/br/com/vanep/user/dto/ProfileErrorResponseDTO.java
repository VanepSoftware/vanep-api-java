package br.com.vanep.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Shared envelope for profile-edit API errors (HTTP 400 and 409). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileErrorResponseDTO(
    String message, String code, String field, Instant retryAfter) {}
