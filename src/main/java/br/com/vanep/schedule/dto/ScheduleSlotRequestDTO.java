package br.com.vanep.schedule.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlotRequestDTO(
    @NotNull(message = "{schedule.slot.weekday.required}") DayOfWeek weekday,
    @NotNull(message = "{schedule.slot.leg.required}") RouteLeg leg,
    @NotNull(message = "{schedule.slot.shift.required}") OperationShift shift,
    @NotNull(message = "{schedule.slot.pickup_time.required}") LocalTime pickupTime) {}
