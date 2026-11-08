package br.com.vanep.schedule.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlotRowDTO(
    Long scheduleId, DayOfWeek weekday, RouteLeg leg, OperationShift shift, LocalTime pickupTime) {}
