package br.com.vanep.contractreadiness.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ContractReadinessDependentDTO(
    String token,
    LocalDate birthDate,
    @Nullable Long schoolId,
    @Nullable Long addressId,
    List<DayOfWeek> schoolDays) {}
