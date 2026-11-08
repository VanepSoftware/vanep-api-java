package br.com.vanep.driver.dto;

import jakarta.validation.constraints.NotNull;

public record DriverAvailabilityRequestDTO(
    @NotNull(message = "{driver.available.required}") Boolean available) {}
