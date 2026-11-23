package br.com.vanep.familymatch.dto;

import org.jspecify.annotations.Nullable;

public record FamilyMatchResponseDTO(
    String token,
    String dependentToken,
    String dependentName,
    @Nullable Integer dependentAge,
    String clientName) {}
