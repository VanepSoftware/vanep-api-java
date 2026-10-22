package br.com.vanep.servicerequest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ServiceRequestItemRequestDTO(
    @NotBlank String dependentToken,
    @NotEmpty(message = "{service_request.days.required}")
        List<@NotNull @Valid ServiceRequestDayRequestDTO> days) {}
