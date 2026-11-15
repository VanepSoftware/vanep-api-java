package br.com.vanep.dependent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DependentSchoolDaysRequestDTO(
    @NotNull List<@NotNull @Valid DependentSchoolDayRequestDTO> days) {}
