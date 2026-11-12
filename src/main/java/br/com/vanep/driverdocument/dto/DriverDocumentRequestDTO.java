package br.com.vanep.driverdocument.dto;

import br.com.vanep.driverdocument.enums.DriverDocumentType;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record DriverDocumentRequestDTO(
    String driverToken,
    @NotNull(message = "{driver_document.type.required}") DriverDocumentType documentType,
    @NotNull(message = "{media.file.required}") MultipartFile file) {}
