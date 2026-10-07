package br.com.vanep.absence.dto;

import jakarta.validation.constraints.NotBlank;

public record AbsenceNoShowRequestDTO(
    @NotBlank(message = "{absence.dependentToken.required}") String dependentToken,
    @NotBlank(message = "{absence.reason.required}") String reason) {}
