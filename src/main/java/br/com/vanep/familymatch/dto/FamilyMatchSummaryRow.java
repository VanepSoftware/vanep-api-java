package br.com.vanep.familymatch.dto;

import java.time.LocalDate;

public record FamilyMatchSummaryRow(
    Long unlinkedPassengerId,
    String token,
    String dependentToken,
    String dependentName,
    LocalDate dependentBirthDate,
    String clientName) {}
