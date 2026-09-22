package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;

public record DriverDocumentSummaryDTO(
    String token,
    DocumentTypeEnum documentType,
    DocumentStatusEnum status,
    String rejectionReason,
    String fileUrl) {}
