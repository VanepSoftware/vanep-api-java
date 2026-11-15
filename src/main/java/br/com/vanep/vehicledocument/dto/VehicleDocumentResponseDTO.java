package br.com.vanep.vehicledocument.dto;

import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleDocumentResponseDTO(
    String token,
    String vehicleToken,
    VehicleDocumentType documentType,
    String documentNumber,
    String fileUrl,
    DocumentFileResponseDTO file,
    LocalDate expiresAt,
    DocumentStatus status,
    ReviewMethod reviewMethod,
    String externalCheckId,
    DocumentRejectionReason rejectionReasonCode,
    String rejectionReason,
    String reviewedByUserToken,
    Instant reviewedAt,
    Instant dataCorrectedAt,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
