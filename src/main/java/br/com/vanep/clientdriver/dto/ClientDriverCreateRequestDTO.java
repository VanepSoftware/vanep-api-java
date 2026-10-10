package br.com.vanep.clientdriver.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientDriverCreateRequestDTO(
    @NotBlank String clientToken, @NotBlank String driverToken) {}
