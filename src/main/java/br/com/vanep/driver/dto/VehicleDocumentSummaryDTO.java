package br.com.vanep.driver.dto;

import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;

public record VehicleDocumentSummaryDTO(
    String token,
    VehicleDocumentType documentType,
    DocumentStatus status,
    String rejectionReason) {}
