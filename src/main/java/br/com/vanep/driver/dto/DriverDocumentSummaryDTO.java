package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import org.jspecify.annotations.Nullable;

public record DriverDocumentSummaryDTO(
    String token,
    DocumentTypeEnum documentType,
    DocumentStatusEnum status,
    @Nullable String rejectionReason,
    @Nullable String fileUrl) {}
