package br.com.vanep.familyinvite.dto;

import org.jspecify.annotations.Nullable;

public record FamilyInvitePreviewResponseDTO(
    @Nullable String driverFirstName,
    @Nullable String schoolName,
    @Nullable String studentFirstName) {}
