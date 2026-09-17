package br.com.vanep.drivercnh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record DriverCnhRequestDTO(
    String driverToken,
    @NotBlank(message = "O número de registro da CNH é obrigatório.")
        @Pattern(regexp = "^\\d{9,11}$", message = "Número de registro da CNH inválido.")
        String registrationNumber,
    @NotBlank(message = "A categoria da CNH é obrigatória.") String category,
    @NotNull(message = "A data de emissão da CNH é obrigatória.") LocalDate issueDate,
    @NotNull(message = "A validade da CNH é obrigatória.") LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    @Pattern(regexp = "^[A-Z]{2}$", message = "UF emissora inválida.") String issuingState,
    String photoUrl) {}
