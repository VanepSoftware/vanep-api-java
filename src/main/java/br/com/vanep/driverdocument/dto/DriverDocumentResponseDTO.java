package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.enums.ReviewMethodEnum;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DriverDocumentResponseDTO(
    String token,
    @Nullable String driverToken,
    DocumentTypeEnum documentType,
    @Nullable String fileUrl,
    LocalDate expiresAt,
    DocumentStatusEnum status,
    ReviewMethodEnum reviewMethod,
    String externalCheckId,
    @Nullable String rejectionReason,
    @Nullable String reviewedByUserToken,
    Instant reviewedAt,
    Instant notifiedAt,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
