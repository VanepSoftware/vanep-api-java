package br.com.vanep.drivercnh.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record DriverCnhRequestDTO(
    String driverToken, @NotNull(message = "{media.file.required}") MultipartFile file) {}
