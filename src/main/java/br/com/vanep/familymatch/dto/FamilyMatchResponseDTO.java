package br.com.vanep.familymatch.dto;

public record FamilyMatchResponseDTO(
    String token,
    String dependentToken,
    String dependentName,
    Integer dependentAge,
    String clientName) {}
