package br.com.vanep.dependent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DependentSchoolDaysRequestDTO(
    @NotNull(message = "{dependent.school_days.days.required}")
        List<
                @NotNull(message = "{dependent.school_days.item.required}") @Valid
                DependentSchoolDayRequestDTO>
            days) {}
