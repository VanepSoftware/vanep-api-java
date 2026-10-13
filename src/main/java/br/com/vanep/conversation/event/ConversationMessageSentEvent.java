package br.com.vanep.conversation.event;

import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;

public record ConversationMessageSentEvent(
    String conversationToken, ConversationMessageResponseDTO message) {}
