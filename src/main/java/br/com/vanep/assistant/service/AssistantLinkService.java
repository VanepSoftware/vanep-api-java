package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantLinkService {

  private final AssistantRepository assistantRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final AssistantMapper mapper;
  private final MessageSource messages;

  public AssistantLinkService(
      AssistantRepository assistantRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      AssistantMapper mapper,
      MessageSource messages) {
    this.assistantRepository = assistantRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional(readOnly = true)
  public List<AssistantListItemResponseDTO> listForDriver(String callerEmail) {
    DriverModel driver = resolveDriver(callerEmail);
    return assistantRepository.findByDriverId(driver.getId()).stream()
        .filter(
            assistant ->
                assistant.getStatus() == AssistantStatus.ACTIVE
                    || assistant.getStatus() == AssistantStatus.INACTIVE)
        .map(mapper::toListItem)
        .toList();
  }

  @Transactional
  public void pause(String callerEmail, String assistantToken) {
    AssistantModel assistant = findAssistantForDriver(callerEmail, assistantToken);
    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("assistant.link.not_active"));
    }
    assistant.setStatus(AssistantStatus.INACTIVE);
    assistantRepository.save(assistant);
  }

  @Transactional
  public void resume(String callerEmail, String assistantToken) {
    AssistantModel assistant = findAssistantForDriver(callerEmail, assistantToken);
    if (assistant.getStatus() != AssistantStatus.INACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.link.not_inactive"));
    }
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistantRepository.save(assistant);
  }

  @Transactional
  public void revokeByDriver(String callerEmail, String assistantToken) {
    AssistantModel assistant = findAssistantForDriver(callerEmail, assistantToken);
    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("assistant.link.not_active"));
    }
    unlink(assistant);
  }

  @Transactional
  public void revokeSelf(String callerEmail) {
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

    AssistantModel assistant =
        assistantRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("assistant.profile.not_found")));

    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("assistant.link.not_active"));
    }

    unlink(assistant);
  }

  private void unlink(AssistantModel assistant) {
    assistant.setStatus(AssistantStatus.UNLINKED);
    assistant.setDriver(null);
    assistant.setActivatedAt(null);
    assistantRepository.save(assistant);
  }

  private AssistantModel findAssistantForDriver(String callerEmail, String assistantToken) {
    DriverModel driver = resolveDriver(callerEmail);
    AssistantModel assistant =
        assistantRepository
            .findByToken(assistantToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("assistant.profile.not_found")));

    if (assistant.getDriver() == null || !assistant.getDriver().getId().equals(driver.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("assistant.link.forbidden"));
    }

    return assistant;
  }

  private DriverModel resolveDriver(String callerEmail) {
    UserModel user =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));
    return driverRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }
}
