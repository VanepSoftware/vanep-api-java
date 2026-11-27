package br.com.vanep.servicerequest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ServiceRequestCreateRequestDTO(
    @NotBlank(message = "{service_request.driver_token.required}") String driverToken,
    @NotNull(message = "{service_request.desired_start_date.required}")
        @FutureOrPresent(message = "{service_request.desired_start_date.past}")
        LocalDate desiredStartDate,
    @Size(max = 1000, message = "{service_request.notes.too_long}") String notes,
    @NotEmpty(message = "{service_request.items.required}")
        List<
                @NotNull(message = "{service_request.item.required}") @Valid
                ServiceRequestItemRequestDTO>
            items) {}
