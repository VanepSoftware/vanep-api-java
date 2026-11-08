package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import java.util.List;

public record DriverOnboardingDocumentsStepDTO(
    boolean completed,
    List<DocumentTypeEnum> missingTypes,
    List<DriverDocumentSummaryDTO> uploadedDocuments) {}
