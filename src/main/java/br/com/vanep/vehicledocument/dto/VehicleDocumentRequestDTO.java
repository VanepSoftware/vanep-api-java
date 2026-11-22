package br.com.vanep.vehicledocument.dto;

import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record VehicleDocumentRequestDTO(
    @NotNull(message = "{vehicle_document.type.required}") VehicleDocumentType documentType,
    @NotNull(message = "{media.file.required}") MultipartFile file) {}
