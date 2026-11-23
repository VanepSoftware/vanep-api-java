package br.com.vanep.driver.dto;

import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import org.jspecify.annotations.Nullable;

public record VehicleDocumentSummaryDTO(
    String token,
    VehicleDocumentType documentType,
    DocumentStatus status,
    @Nullable String rejectionReason,
    @Nullable String fileUrl) {}
