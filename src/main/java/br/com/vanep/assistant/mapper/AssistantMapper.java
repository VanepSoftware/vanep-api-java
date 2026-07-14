package br.com.vanep.assistant.mapper;

import br.com.vanep.assistant.dto.AssistantInviteResponseDTO;
import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import org.springframework.stereotype.Component;

@Component
public class AssistantMapper {

  public AssistantInviteResponseDTO toInviteResponse(AssistantInviteModel invite) {
    return new AssistantInviteResponseDTO(
        invite.getToken(),
        invite.getAssistant().getUser().getEmail(),
        invite.getStatus(),
        invite.getExpiresAt(),
        invite.getCreatedAt());
  }

  public AssistantListItemResponseDTO toListItem(AssistantModel assistant) {
    return new AssistantListItemResponseDTO(
        assistant.getToken(),
        assistant.getUser().getName(),
        assistant.getUser().getEmail(),
        assistant.getPhoto(),
        assistant.getStatus(),
        assistant.getActivatedAt());
  }
}
