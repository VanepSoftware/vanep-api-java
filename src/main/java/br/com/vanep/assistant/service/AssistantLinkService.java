package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantLinkService {

  private final AssistantRepository assistantRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final AssistantMapper mapper;

  public AssistantLinkService(
      AssistantRepository assistantRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      UserService userService,
      AssistantMapper mapper) {
    this.assistantRepository = assistantRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.userService = userService;
    this.mapper = mapper;
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
      throw ApiProblemException.conflict("assistant.link.not_active");
    }
    assistant.setStatus(AssistantStatus.INACTIVE);
    assistantRepository.save(assistant);
  }

  @Transactional
  public void resume(String callerEmail, String assistantToken) {
    AssistantModel assistant = findAssistantForDriver(callerEmail, assistantToken);
    if (assistant.getStatus() != AssistantStatus.INACTIVE) {
      throw ApiProblemException.conflict("assistant.link.not_inactive");
    }
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistantRepository.save(assistant);
  }

  @Transactional
  public void revokeByDriver(String callerEmail, String assistantToken) {
    AssistantModel assistant = findAssistantForDriver(callerEmail, assistantToken);
    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw ApiProblemException.conflict("assistant.link.not_active");
    }
    unlink(assistant);
  }

  @Transactional
  public void revokeSelf(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.ASSISTANT);

    AssistantModel assistant =
        assistantRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> ApiProblemException.notFound("assistant.profile.not_found"));

    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw ApiProblemException.conflict("assistant.link.not_active");
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
            .orElseThrow(() -> ApiProblemException.notFound("assistant.profile.not_found"));

    if (assistant.getDriver() == null || !assistant.getDriver().getId().equals(driver.getId())) {
      throw ApiProblemException.forbidden("assistant.link.forbidden");
    }

    return assistant;
  }

  private DriverModel resolveDriver(String callerEmail) {
    UserModel user =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(() -> ApiProblemException.notFound("user.account.not_found"));
    return driverRepository
        .findByUserId(user.getId())
        .orElseThrow(() -> ApiProblemException.notFound("user.driver_profile.not_found"));
  }
}
