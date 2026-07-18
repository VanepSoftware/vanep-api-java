package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantProfileResponseDTO;
import br.com.vanep.assistant.dto.AssistantProfileUpdateRequestDTO;
import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantProfileService {

  private final AssistantRepository assistantRepository;
  private final AssistantInviteRepository inviteRepository;
  private final UserRepository userRepository;
  private final AssistantMapper mapper;
  private final MessageSource messages;

  public AssistantProfileService(
      AssistantRepository assistantRepository,
      AssistantInviteRepository inviteRepository,
      UserRepository userRepository,
      AssistantMapper mapper,
      MessageSource messages) {
    this.assistantRepository = assistantRepository;
    this.inviteRepository = inviteRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional(readOnly = true)
  public AssistantProfileResponseDTO getProfile(String callerEmail) {
    AssistantModel assistant = requireAssistant(callerEmail);
    return mapper.toProfile(assistant, resolvePendingInvite(assistant));
  }

  @Transactional
  public AssistantProfileResponseDTO updateProfile(
      String callerEmail, AssistantProfileUpdateRequestDTO request) {
    AssistantModel assistant = requireAssistant(callerEmail);
    assistant.setPhoto(request.photo());
    assistant = assistantRepository.save(assistant);
    return mapper.toProfile(assistant, resolvePendingInvite(assistant));
  }

  private AssistantModel requireAssistant(String callerEmail) {
    UserModel user =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    if (user.getType() != UserType.ASSISTANT) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("assistant.link.forbidden"));
    }

    return assistantRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("assistant.profile.not_found")));
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
