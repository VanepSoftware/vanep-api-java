package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DriverDocumentRequestDTO(
    String driverToken,
    @NotNull(message = "O tipo do documento é obrigatório.") DocumentTypeEnum documentType,
    @NotBlank(message = "A URL do arquivo é obrigatória.") String fileUrl,
    LocalDate expiresAt) {}
