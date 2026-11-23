package br.com.vanep.drivercompliance.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentKind;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SubmissionResponseDTO(
    DocumentKind kind,
    String token,
    @Nullable String documentNumber,
    @Nullable LocalDate expiresAt,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    @Nullable DocumentRejectionReason rejectionReasonCode,
    @Nullable String rejectionReason,
    @Nullable ActorResponseDTO reviewedBy,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    @Nullable ActorResponseDTO dataCorrectedBy,
    Instant createdAt,
    Long version,
    @Nullable String fileUrl,
    @Nullable DocumentFileResponseDTO file,
    @Nullable String category,
    @Nullable LocalDate issueDate,
    @Nullable String registrationNumber,
    @Nullable LocalDate firstLicenseDate,
    @Nullable String securityNumber,
    @Nullable String issuingState) {

  public record ActorResponseDTO(String token, String name) {}
}
