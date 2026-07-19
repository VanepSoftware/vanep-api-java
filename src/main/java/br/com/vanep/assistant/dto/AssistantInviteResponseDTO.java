package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import java.time.Instant;

public record AssistantInviteResponseDTO(
    String token,
    String assistantEmail,
    AssistantInviteStatus status,
    Instant expiresAt,
    Instant createdAt) {}
