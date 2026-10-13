package br.com.vanep.conversation.dto;

import br.com.vanep.conversation.enums.ConversationParticipant;
import java.time.Instant;

public record ConversationMessageResponseDTO(
    String token, String body, ConversationParticipant sender, Instant sentAt) {}
