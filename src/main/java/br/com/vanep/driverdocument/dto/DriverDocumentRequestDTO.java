package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DriverDocumentRequestDTO(
    String driverToken,
    @NotNull(message = "O tipo do documento é obrigatório.") DriverDocumentType documentType,
    LocalDate expiresAt) {}
