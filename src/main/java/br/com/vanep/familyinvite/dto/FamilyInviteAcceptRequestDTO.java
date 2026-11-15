package br.com.vanep.familyinvite.dto;

import jakarta.validation.constraints.NotBlank;

public record FamilyInviteAcceptRequestDTO(
    @NotBlank(message = "{family_invite.dependent.required}") String dependentToken) {}
