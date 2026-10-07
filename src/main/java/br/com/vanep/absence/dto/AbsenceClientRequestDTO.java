package br.com.vanep.absence.dto;

import br.com.vanep.absence.enums.AbsenceScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AbsenceClientRequestDTO(
    @NotBlank(message = "{absence.dependentToken.required}") String dependentToken,
    @NotNull(message = "{absence.scope.required}") AbsenceScope scope) {}
