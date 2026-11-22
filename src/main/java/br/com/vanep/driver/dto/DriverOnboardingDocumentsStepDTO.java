package br.com.vanep.driver.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import java.util.List;

public record DriverOnboardingDocumentsStepDTO(
    boolean completed,
    List<DriverDocumentType> missingDriverTypes,
    List<DriverDocumentSummaryDTO> uploadedDocuments,
    List<VehicleDocumentsStepDTO> vehicles) {}
