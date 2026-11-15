package br.com.vanep.familyinvite.dto;

import java.time.Instant;

public record FamilyInviteResponseDTO(String code, String inviteUrl, Instant sentAt) {}
