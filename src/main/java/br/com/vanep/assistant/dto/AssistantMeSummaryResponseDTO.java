package br.com.vanep.assistant.dto;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.user.dto.UserMeResponseDTO;
import org.jspecify.annotations.Nullable;

public record AssistantMeSummaryResponseDTO(
    String token,
    @Nullable String photo,
    AssistantStatus status,
    @Nullable AssistantPendingInviteDTO pendingInvite,
    UserMeResponseDTO user) {}
