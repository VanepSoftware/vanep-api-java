package br.com.vanep.assistant.dto;

import java.time.Instant;

public record AssistantPendingInviteDTO(
    String token, Instant expiresAt, AssistantDriverSummaryDTO driver) {}
