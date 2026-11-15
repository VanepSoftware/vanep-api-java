package br.com.vanep.driver.dto;

import br.com.vanep.driver.enums.DriverRejectionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverRejectionRequestDTO(
    @NotNull(message = "{driver.rejection.reason_code.required}") DriverRejectionReason reasonCode,
    @Size(max = 255, message = "{driver.rejection.reason.max_length}") String reason) {}
