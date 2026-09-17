package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.user.dto.UserMeResponseDTO;

public record AssistantMeSummaryResponseDTO(
    String token,
    String photo,
    AssistantStatus status,
    AssistantPendingInviteDTO pendingInvite,
    UserMeResponseDTO user) {}
