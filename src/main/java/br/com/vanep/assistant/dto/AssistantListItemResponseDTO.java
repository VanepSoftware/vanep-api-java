package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record AssistantListItemResponseDTO(
    String token,
    String name,
    String email,
    @Nullable String photo,
    AssistantStatus status,
    @Nullable Instant activatedAt) {}
