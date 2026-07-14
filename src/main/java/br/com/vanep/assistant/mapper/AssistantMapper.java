package br.com.vanep.assistant.mapper;

import br.com.vanep.assistant.dto.AssistantDriverSummaryDTO;
import br.com.vanep.assistant.dto.AssistantInviteResponseDTO;
import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.dto.AssistantPendingInviteDTO;
import br.com.vanep.assistant.dto.AssistantProfileResponseDTO;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.driver.model.DriverModel;
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

  public AssistantProfileResponseDTO toProfile(
      AssistantModel assistant, AssistantPendingInviteDTO pendingInvite) {
    return new AssistantProfileResponseDTO(
        assistant.getToken(),
        assistant.getUser().getName(),
        assistant.getUser().getEmail(),
        assistant.getPhoto(),
        assistant.getStatus(),
        assistant.getActivatedAt(),
        pendingInvite);
  }

  public AssistantPendingInviteDTO toPendingInvite(AssistantInviteModel invite) {
    return new AssistantPendingInviteDTO(
        invite.getToken(), invite.getExpiresAt(), toDriverSummary(invite.getDriver()));
  }

  public AssistantDriverSummaryDTO toDriverSummary(DriverModel driver) {
    return new AssistantDriverSummaryDTO(
        driver.getUser().getName(), driver.getPhoto(), driver.getCity(), driver.getRating());
  }
}
