package br.com.vanep.assistant.mapper;

import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.dto.AssistantProfileResponseDTO;
import br.com.vanep.assistant.model.AssistantModel;
import org.springframework.stereotype.Component;

@Component
public class AssistantMapper {

  public AssistantProfileResponseDTO toProfile(AssistantModel assistant) {
    return new AssistantProfileResponseDTO(
        assistant.getToken(),
        assistant.getStatus(),
        assistant.getUser().getName(),
        assistant.getPhoto(),
        assistant.getVerificationStatus(),
        assistant.getActivatedAt(),
        assistant.getCreatedAt());
  }

  public AssistantListItemResponseDTO toListItem(AssistantModel assistant) {
    return new AssistantListItemResponseDTO(
        assistant.getToken(),
        assistant.getStatus(),
        assistant.getUser().getName(),
        assistant.getPhoto(),
        assistant.getActivatedAt(),
        assistant.getCreatedAt());
  }
}
