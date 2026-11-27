package br.com.vanep.servicerequest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ServiceRequestItemRequestDTO(
    @NotBlank(message = "{service_request.dependent_token.required}") String dependentToken,
    @NotEmpty(message = "{service_request.days.required}")
        List<
                @NotNull(message = "{service_request.day.item.required}") @Valid
                ServiceRequestDayRequestDTO>
            days) {}
