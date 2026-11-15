package br.com.vanep.dependent.dto;

import br.com.vanep.shared.enums.SchoolShift;
import java.time.DayOfWeek;

public record DependentSchoolDayResponseDTO(DayOfWeek weekday, SchoolShift schoolShift) {}
