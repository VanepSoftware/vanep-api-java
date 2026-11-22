package br.com.vanep.drivercnh.dto;

import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;

public record DriverCnhStatusUpdateRequestDTO(
    @NotNull(message = "{document.review.status.required}") DocumentDecisionStatus status,
    ReviewMethod reviewMethod,
    @Size(max = 64, message = "{document.review.external_check_id.too_long}")
        String externalCheckId,
    DocumentRejectionReason rejectionReasonCode,
    @Size(max = 255, message = "{document.review.reason.too_long}") String rejectionReason,
    @Pattern(regexp = "^\\d{9,11}$", message = "Número de registro da CNH inválido.")
        String registrationNumber,
    @Pattern(regexp = "^(D|E|AD|AE)$", message = "{driver_cnh.category.invalid}") String category,
    LocalDate issueDate,
    LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    @Pattern(regexp = "^[A-Z]{2}$", message = "UF emissora inválida.") String issuingState,
    @NotNull(message = "{document.review.version.required}") Long version) {

  public DriverCnhStatusUpdateRequestDTO {
    category =
        category == null || category.isBlank() ? null : category.strip().toUpperCase(Locale.ROOT);
  }

  public DocumentDecision toDecision() {
    return new DocumentDecision(
        status, reviewMethod, externalCheckId, rejectionReasonCode, rejectionReason);
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
