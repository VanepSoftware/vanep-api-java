package br.com.vanep.drivercompliance.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;

public record SubmissionResponseDTO(
    DocumentKind kind,
    String token,
    String documentNumber,
    LocalDate expiresAt,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    DocumentRejectionReason rejectionReasonCode,
    String rejectionReason,
    ActorResponseDTO reviewedBy,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    ActorResponseDTO dataCorrectedBy,
    Instant createdAt,
    Long version,
    String fileUrl,
    DocumentFileResponseDTO file,
    String category,
    LocalDate issueDate,
    String registrationNumber,
    LocalDate firstLicenseDate,
    String securityNumber,
    String issuingState) {

  public record ActorResponseDTO(String token, String name) {}
}
