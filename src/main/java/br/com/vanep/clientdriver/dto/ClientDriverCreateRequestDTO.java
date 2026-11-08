package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.LinkOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientDriverCreateRequestDTO(
    @NotBlank String clientToken, @NotBlank String driverToken, @NotNull LinkOrigin origin) {}
