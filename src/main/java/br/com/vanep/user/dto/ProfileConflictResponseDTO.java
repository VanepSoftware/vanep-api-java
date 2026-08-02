package br.com.vanep.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileConflictResponseDTO(
    String message, String code, String field, Instant retryAfter) {}
