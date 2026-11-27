package br.com.vanep.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VehicleRequestDTO(
    String driverToken,
    @NotBlank(message = "{vehicle.plate.required}")
        @Pattern(regexp = "^[A-Z]{3}-?\\d[A-Z0-9]\\d{2}$", message = "{vehicle.plate.invalid}")
        String plate,
    @NotBlank(message = "{vehicle.brand.required}") String brand,
    @NotBlank(message = "{vehicle.model.required}") String model,
    @NotNull(message = "{vehicle.manufacture_year.required}")
        @Min(value = 1900, message = "{vehicle.manufacture_year.invalid}")
        @Max(value = 2100, message = "{vehicle.manufacture_year.invalid}")
        Integer manufactureYear,
    @NotBlank(message = "{vehicle.color.required}") String color,
    @NotNull(message = "{vehicle.capacity.required}")
        @Min(value = 1, message = "{vehicle.capacity.min}")
        Integer capacity) {}
