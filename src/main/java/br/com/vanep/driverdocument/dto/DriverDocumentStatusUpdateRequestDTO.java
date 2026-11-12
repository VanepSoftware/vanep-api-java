package br.com.vanep.driverdocument.dto;

import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import jakarta.validation.constraints.NotNull;

public record DriverDocumentStatusUpdateRequestDTO(
    @NotNull(message = "O status do documento é obrigatório.") DocumentStatus status,
    ReviewMethod reviewMethod,
    String externalCheckId,
    String rejectionReason) {}
