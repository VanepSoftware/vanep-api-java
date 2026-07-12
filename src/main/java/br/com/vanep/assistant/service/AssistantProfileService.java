package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantProfileResponseDTO;
import br.com.vanep.assistant.dto.AssistantProfileUpdateRequestDTO;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantProfileService {

  private final AssistantRepository assistants;
  private final UserRepository users;
  private final AssistantMapper mapper;
  private final MessageSource messages;

  public AssistantProfileService(
      AssistantRepository assistants,
      UserRepository users,
      AssistantMapper mapper,
      MessageSource messages) {
    this.assistants = assistants;
    this.users = users;
    this.mapper = mapper;
    this.messages = messages;
  }

  public AssistantProfileResponseDTO getProfile(String callerEmail) {
    return mapper.toProfile(requireAssistantByEmail(callerEmail));
  }

  @Transactional
  public AssistantProfileResponseDTO updateProfile(
      String callerEmail, AssistantProfileUpdateRequestDTO request) {
    AssistantModel assistant = requireAssistantByEmail(callerEmail);
    assistant.setPhoto(request.photo());
    return mapper.toProfile(assistants.save(assistant));
  }

  private AssistantModel requireAssistantByEmail(String callerEmail) {
    UserModel caller = requireUserByEmail(callerEmail);
    return assistants
        .findByUserId(caller.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("assistant.not_found")));
  }

  private UserModel requireUserByEmail(String callerEmail) {
    return users
        .findByEmail(callerEmail)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.account.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
