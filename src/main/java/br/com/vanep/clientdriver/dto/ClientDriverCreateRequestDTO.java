package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import jakarta.validation.constraints.NotBlank;

public record ClientDriverCreateRequestDTO(
    @NotBlank String clientToken, @NotBlank String driverToken, RelationshipStatus status) {}
