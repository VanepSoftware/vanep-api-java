package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import java.time.Instant;
import java.time.LocalDate;

public record DriverDocumentResponseDTO(
    String token,
    String driverToken,
    DriverDocumentType documentType,
    String fileUrl,
    LocalDate expiresAt,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    String externalCheckId,
    String rejectionReason,
    String reviewedByUserToken,
    Instant reviewedAt,
    Instant notifiedAt,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
