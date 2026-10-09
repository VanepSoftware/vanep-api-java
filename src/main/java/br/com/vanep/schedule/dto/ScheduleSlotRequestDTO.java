package br.com.vanep.schedule.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlotRequestDTO(
    @NotNull DayOfWeek weekday,
    @NotNull RouteLeg leg,
    @NotNull OperationShift shift,
    @NotNull LocalTime windowStart,
    LocalTime windowEnd) {}
