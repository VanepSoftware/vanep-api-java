package br.com.vanep.drivercnh.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Locale;

public record DriverCnhDataCorrectionRequestDTO(
    @Pattern(regexp = "^\\d{9,11}$", message = "Número de registro da CNH inválido.")
        String registrationNumber,
    @Pattern(regexp = "^(D|E|AD|AE)$", message = "{driver_cnh.category.invalid}") String category,
    LocalDate issueDate,
    LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    @Pattern(regexp = "^[A-Z]{2}$", message = "UF emissora inválida.") String issuingState,
    @NotNull(message = "{document.review.version.required}") Long version) {

  public DriverCnhDataCorrectionRequestDTO {
    category =
        category == null || category.isBlank() ? null : category.strip().toUpperCase(Locale.ROOT);
  }

  public DriverCnhDataDTO data() {
    return new DriverCnhDataDTO(
        registrationNumber,
        category,
        issueDate,
        validUntil,
        firstLicenseDate,
        securityNumber,
        issuingState);
  }
}
