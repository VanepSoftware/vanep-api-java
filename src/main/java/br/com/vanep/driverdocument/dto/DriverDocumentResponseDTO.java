package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.enums.ReviewMethodEnum;
import java.time.Instant;
import java.time.LocalDate;

public record DriverDocumentResponseDTO(
    String token,
    String driverToken,
    DocumentTypeEnum documentType,
    String fileUrl,
    LocalDate expiresAt,
    DocumentStatusEnum status,
    ReviewMethodEnum reviewMethod,
    String externalCheckId,
    String rejectionReason,
    String reviewedByUserToken,
    Instant reviewedAt,
    Instant notifiedAt,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
