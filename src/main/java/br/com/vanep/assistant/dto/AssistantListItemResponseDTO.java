package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import java.time.Instant;

public record AssistantListItemResponseDTO(
    String token,
    AssistantStatus status,
    String name,
    String photo,
    Instant activatedAt,
    Instant createdAt) {}
