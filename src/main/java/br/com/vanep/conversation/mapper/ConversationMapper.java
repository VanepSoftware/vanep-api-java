package br.com.vanep.conversation.mapper;

import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.dto.ConversationResponseDTO;
import br.com.vanep.conversation.model.ConversationMessageModel;
import br.com.vanep.conversation.model.ConversationModel;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

  public ConversationResponseDTO toResponse(ConversationModel conversation) {
    return new ConversationResponseDTO(
        conversation.getToken(),
        conversation.getDriver().getToken(),
        conversation.getDriver().getUser().getName(),
        conversation.getClient().getUser().getName(),
        conversation.getLastMessageAt());
  }

  public ConversationMessageResponseDTO toMessageResponse(ConversationMessageModel message) {
    return new ConversationMessageResponseDTO(
        message.getToken(), message.getBody(), message.getSender(), message.getSentAt());
  }
}
