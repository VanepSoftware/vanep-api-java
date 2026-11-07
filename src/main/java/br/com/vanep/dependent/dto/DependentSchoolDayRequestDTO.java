package br.com.vanep.dependent.dto;

import br.com.vanep.shared.enums.SchoolShift;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record DependentSchoolDayRequestDTO(
    @NotNull DayOfWeek weekday, @NotNull SchoolShift schoolShift) {}
