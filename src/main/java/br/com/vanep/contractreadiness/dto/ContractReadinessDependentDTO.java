package br.com.vanep.contractreadiness.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record ContractReadinessDependentDTO(
    String token, LocalDate birthDate, Long schoolId, Long addressId, List<DayOfWeek> schoolDays) {}
