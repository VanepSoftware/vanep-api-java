package br.com.vanep.absence.dto;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceSource;
import java.time.Instant;
import java.time.LocalDate;

public record AbsenceResponseDTO(
    String token,
    String dependentToken,
    String clientDriverToken,
    String tripToken,
    LocalDate absenceDate,
    AbsenceLeg leg,
    AbsenceSource source,
    String reason,
    Instant notifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
