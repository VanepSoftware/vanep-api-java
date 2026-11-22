package br.com.vanep.familyinvite.dto;

import org.jspecify.annotations.Nullable;

public record FamilyInviteAcceptResponseDTO(
    String clientDriverToken, @Nullable String familyMatchToken) {}
