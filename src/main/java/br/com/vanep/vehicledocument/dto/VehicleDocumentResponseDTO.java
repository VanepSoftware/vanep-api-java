package br.com.vanep.vehicledocument.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record VehicleDocumentResponseDTO(
    String token,
    String vehicleToken,
    VehicleDocumentType documentType,
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
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
