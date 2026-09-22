package br.com.vanep.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverRejectionRequestDTO(
    @NotBlank(message = "{driver.rejection.reason.required}")
        @Size(max = 255, message = "{driver.rejection.reason.max_length}")
        String reason) {}
