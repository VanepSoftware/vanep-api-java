package br.com.vanep.driver.dto;

import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import java.util.List;

public record VehicleDocumentsStepDTO(
    String vehicleToken,
    String plate,
    boolean completed,
    List<VehicleDocumentType> missingTypes,
    List<VehicleDocumentSummaryDTO> uploadedDocuments) {}
