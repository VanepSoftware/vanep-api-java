package br.com.vanep.servicerequest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record ServiceRequestDayRequestDTO(
    @NotNull(message = "{service_request.day.weekday.required}") DayOfWeek weekday,
    @NotNull(message = "{service_request.day.needs_outbound.required}") Boolean needsOutbound,
    @NotNull(message = "{service_request.day.needs_return.required}") Boolean needsReturn) {}
