package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantInviteViewState;
import java.time.Instant;

public record AssistantInvitePageDTO(
    AssistantInviteViewState state,
    String driverName,
    String driverPhoto,
    String driverCity,
    String driverRating,
    Instant expiresAt) {

  public static AssistantInvitePageDTO ofState(AssistantInviteViewState state) {
    return new AssistantInvitePageDTO(state, null, null, null, null, null);
  }
}
