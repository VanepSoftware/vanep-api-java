package br.com.vanep.conversation.dto;

import java.time.Instant;

public record ConversationResponseDTO(
    String token,
    String driverToken,
    String driverName,
    String clientName,
    Instant lastMessageAt) {}
