package br.com.vanep.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VehicleRequestDTO(
    String driverToken,
    @NotBlank(message = "A placa do veículo é obrigatória.")
        @Pattern(regexp = "^[A-Z]{3}-?\\d[A-Z0-9]\\d{2}$", message = "Placa em formato inválido.")
        String plate,
    @NotBlank(message = "A marca do veículo é obrigatória.") String brand,
    @NotBlank(message = "O modelo do veículo é obrigatório.") String model,
    @NotNull(message = "O ano do veículo é obrigatório.")
        @Min(value = 1900, message = "Ano inválido.")
        @Max(value = 2100, message = "Ano inválido.")
        Integer manufactureYear,
    @NotBlank(message = "A cor do veículo é obrigatória.") String color,
    @NotNull(message = "A capacidade de assentos é obrigatória.")
        @Min(value = 1, message = "A capacidade deve ser de pelo menos 1 assento.")
        Integer capacity,
    String photoFrontUrl,
    String photoSideUrl,
    String photoDocumentUrl) {}
