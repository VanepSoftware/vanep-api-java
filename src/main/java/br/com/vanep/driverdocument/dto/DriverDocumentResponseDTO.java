package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DriverDocumentResponseDTO(
    String token,
    @Nullable String driverToken,
    DriverDocumentType documentType,
    @Nullable String documentNumber,
    @Nullable String fileUrl,
    @Nullable DocumentFileResponseDTO file,
    @Nullable LocalDate expiresAt,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    String externalCheckId,
    @Nullable DocumentRejectionReason rejectionReasonCode,
    @Nullable String rejectionReason,
    @Nullable String reviewedByUserToken,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    Instant notifiedAt,
    boolean active,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
