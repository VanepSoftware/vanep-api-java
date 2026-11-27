package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantMeSummaryResponseDTO;
import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantProfileService {

  private final AssistantRepository assistantRepository;
  private final AssistantInviteRepository inviteRepository;
  private final UserService userService;
  private final AssistantMapper mapper;

  public AssistantProfileService(
      AssistantRepository assistantRepository,
      AssistantInviteRepository inviteRepository,
      UserService userService,
      AssistantMapper mapper) {
    this.assistantRepository = assistantRepository;
    this.inviteRepository = inviteRepository;
    this.userService = userService;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public AssistantMeSummaryResponseDTO getProfile(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.ASSISTANT);
    AssistantModel assistant = requireByUserId(user.getId());
    return mapper.toMeSummary(
        assistant, userService.toMeResponse(user), resolvePendingInvite(assistant));
  }

  private AssistantModel requireByUserId(Long userId) {
    return assistantRepository
        .findByUserId(userId)
        .orElseThrow(() -> ApiProblemException.notFound("assistant.profile.not_found"));
  }

  private br.com.vanep.assistant.dto.AssistantPendingInviteDTO resolvePendingInvite(
      AssistantModel assistant) {
    if (assistant.getStatus() != AssistantStatus.PENDING) {
      return null;
    }
    return inviteRepository
        .findByAssistantIdAndStatus(assistant.getId(), AssistantInviteStatus.PENDING)
        .map(mapper::toPendingInvite)
        .orElse(null);
  }
}
