package br.com.vanep.servicerequest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record ServiceRequestDayRequestDTO(
    @NotNull DayOfWeek weekday, @NotNull Boolean needsOutbound, @NotNull Boolean needsReturn) {}
