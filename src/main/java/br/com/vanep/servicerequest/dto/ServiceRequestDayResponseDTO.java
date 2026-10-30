package br.com.vanep.servicerequest.dto;

import br.com.vanep.shared.enums.SchoolShift;
import java.time.DayOfWeek;

public record ServiceRequestDayResponseDTO(
    DayOfWeek weekday, SchoolShift schoolShift, boolean needsOutbound, boolean needsReturn) {}
