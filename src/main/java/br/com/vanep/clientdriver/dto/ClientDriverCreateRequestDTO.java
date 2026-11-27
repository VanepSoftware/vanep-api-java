package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.LinkOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientDriverCreateRequestDTO(
    @NotBlank(message = "{client_driver.client_token.required}") String clientToken,
    @NotBlank(message = "{client_driver.driver_token.required}") String driverToken,
    @NotNull(message = "{client_driver.origin.required}") LinkOrigin origin) {}
