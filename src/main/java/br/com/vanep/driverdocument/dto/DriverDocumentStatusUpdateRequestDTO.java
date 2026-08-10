package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.ReviewMethodEnum;
import jakarta.validation.constraints.NotNull;

public record DriverDocumentStatusUpdateRequestDTO(
    @NotNull(message = "O status do documento é obrigatório.") DocumentStatusEnum status,
    ReviewMethodEnum reviewMethod,
    String externalCheckId,
    String rejectionReason) {}
