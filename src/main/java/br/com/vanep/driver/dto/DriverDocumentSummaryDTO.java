package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.enums.DocumentStatus;
import org.jspecify.annotations.Nullable;

public record DriverDocumentSummaryDTO(
    String token,
    DriverDocumentType documentType,
    DocumentStatus status,
    @Nullable String rejectionReason,
    @Nullable String fileUrl) {}
