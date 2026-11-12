package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.shared.document.enums.DocumentStatus;

public record DriverDocumentSummaryDTO(
    String token,
    DriverDocumentType documentType,
    DocumentStatus status,
    String rejectionReason,
    String fileUrl) {}
