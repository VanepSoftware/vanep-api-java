package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.assistant.security.AssistantSecurityService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverLinkCodeGenerator;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverLinkCodeGenerateResponseDTO;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.repository.DriverLinkCodeRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantLinkService {

  private final AssistantRepository assistants;
  private final DriverRepository drivers;
  private final DriverLinkCodeRepository linkCodes;
  private final UserRepository users;
  private final DriverLinkCodeConsumer codeConsumer;
  private final AssistantMapper mapper;
  private final AssistantSecurityService assistantSecurity;
  private final MessageSource messages;

  public AssistantLinkService(
      AssistantRepository assistants,
      DriverRepository drivers,
      DriverLinkCodeRepository linkCodes,
      UserRepository users,
      DriverLinkCodeConsumer codeConsumer,
      AssistantMapper mapper,
      AssistantSecurityService assistantSecurity,
      MessageSource messages) {
    this.assistants = assistants;
    this.drivers = drivers;
    this.linkCodes = linkCodes;
    this.users = users;
    this.codeConsumer = codeConsumer;
    this.mapper = mapper;
    this.assistantSecurity = assistantSecurity;
    this.messages = messages;
  }

  @Transactional
  public DriverLinkCodeGenerateResponseDTO generateLinkCode(String callerEmail) {
    DriverModel driver = requireDriverByEmail(callerEmail);
    linkCodes
        .findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE)
        .ifPresent(
            existing -> {
              existing.setStatus(DriverLinkCodeStatus.CANCELLED);
              linkCodes.save(existing);
            });

    String plaintext = DriverLinkCodeGenerator.generate();
    Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
    DriverLinkCodeModel code = new DriverLinkCodeModel();
    code.setDriver(driver);
    code.setCodeHash(SecureTokens.hash(plaintext));
    code.setStatus(DriverLinkCodeStatus.ACTIVE);
    code.setExpiresAt(expiresAt);
    linkCodes.save(code);
    return new DriverLinkCodeGenerateResponseDTO(plaintext, expiresAt);
  }

  @Transactional
  public void cancelCurrentLinkCode(String callerEmail) {
    DriverModel driver = requireDriverByEmail(callerEmail);
    DriverLinkCodeModel active =
        linkCodes
            .findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("driver_link_code.none_active")));
    active.setStatus(DriverLinkCodeStatus.CANCELLED);
    linkCodes.save(active);
  }

  @Transactional
  public void consumeLinkCode(String callerEmail, String plaintextCode) {
    AssistantModel assistant = requireAssistantByEmail(callerEmail);
    if (assistant.getStatus() != AssistantStatus.UNLINKED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.not_eligible_for_consume"));
    }
    try {
      codeConsumer.consumeAndActivate(assistant, plaintextCode);
    } catch (InvalidDriverLinkCodeException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  public List<AssistantListItemResponseDTO> listForDriver(String callerEmail) {
    DriverModel driver = requireDriverByEmail(callerEmail);
    return assistants.findByDriverId(driver.getId()).stream().map(mapper::toListItem).toList();
  }

  @Transactional
  public void pause(String assistantToken, String callerEmail) {
    AssistantModel assistant = requireOwnedAssistant(assistantToken, callerEmail);
    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invalid_status_for_pause"));
    }
    assistant.setStatus(AssistantStatus.INACTIVE);
    assistants.save(assistant);
  }

  @Transactional
  public void resume(String assistantToken, String callerEmail) {
    AssistantModel assistant = requireOwnedAssistant(assistantToken, callerEmail);
    if (assistant.getStatus() != AssistantStatus.INACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invalid_status_for_resume"));
    }
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistants.save(assistant);
  }

  @Transactional
  public void revoke(String assistantToken, String callerEmail) {
    AssistantModel assistant = requireOwnedAssistant(assistantToken, callerEmail);
    revokeActiveAssistant(assistant);
  }

  @Transactional
  public void revokeByAssistant(String callerEmail) {
    AssistantModel assistant = requireAssistantByEmail(callerEmail);
    revokeActiveAssistant(assistant);
  }

  private void revokeActiveAssistant(AssistantModel assistant) {
    if (assistant.getStatus() != AssistantStatus.ACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invalid_status_for_revoke"));
    }
    assistant.setStatus(AssistantStatus.UNLINKED);
    assistant.setDriver(null);
    assistants.save(assistant);
  }

  private DriverModel requireDriverByEmail(String callerEmail) {
    UserModel caller = requireUserByEmail(callerEmail);
    return drivers
        .findByUserId(caller.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }

  private AssistantModel requireAssistantByEmail(String callerEmail) {
    UserModel caller = requireUserByEmail(callerEmail);
    return assistants
        .findByUserId(caller.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("assistant.not_found")));
  }

  private AssistantModel requireOwnedAssistant(String assistantToken, String callerEmail) {
    UserModel caller = requireUserByEmail(callerEmail);
    AssistantModel assistant = requireByToken(assistantToken);
    if (!assistantSecurity.isDriverOwnerOfAssistant(assistantToken, caller.getToken())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("assistant.forbidden"));
    }
    return assistant;
  }

  private UserModel requireUserByEmail(String callerEmail) {
    return users
        .findByEmail(callerEmail)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.account.not_found")));
  }

  private AssistantModel requireByToken(String token) {
    return assistants
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("assistant.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
