package br.com.vanep.dependent.dto;

import br.com.vanep.shared.enums.SchoolShift;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record DependentSchoolDayRequestDTO(
    @NotNull(message = "{dependent.school_days.weekday.required}") DayOfWeek weekday,
    @NotNull(message = "{dependent.school_days.school_shift.required}") SchoolShift schoolShift) {}
