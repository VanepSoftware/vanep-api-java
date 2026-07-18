package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import java.time.Instant;

public record AssistantProfileResponseDTO(
    String token,
    String name,
    String email,
    String photo,
    AssistantStatus status,
    Instant activatedAt,
    AssistantPendingInviteDTO pendingInvite) {}
