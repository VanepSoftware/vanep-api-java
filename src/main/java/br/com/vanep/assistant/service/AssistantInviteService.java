package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantInviteCreateRequestDTO;
import br.com.vanep.assistant.dto.AssistantInviteResponseDTO;
import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.mapper.AssistantMapper;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantInviteService {

  private static final Duration REJECTION_COOLDOWN = Duration.ofDays(7);

  private final AssistantInviteRepository inviteRepository;
  private final AssistantRepository assistantRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final MailService mail;
  private final AssistantMapper mapper;
  private final MessageSource messages;
  private final Duration inviteTtl;

  public AssistantInviteService(
      AssistantInviteRepository inviteRepository,
      AssistantRepository assistantRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      MailService mail,
      AssistantMapper mapper,
      MessageSource messages,
      @Value("${vanep.mail.assistant-invite-ttl-hours:72}") long inviteTtlHours) {
    this.inviteRepository = inviteRepository;
    this.assistantRepository = assistantRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mail = mail;
    this.mapper = mapper;
    this.messages = messages;
    this.inviteTtl = Duration.ofHours(inviteTtlHours);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public AssistantInviteResponseDTO createInvite(
      String callerEmail, AssistantInviteCreateRequestDTO request) {
    DriverModel driver = resolveDriver(callerEmail);
    UserModel user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("assistant.invite.email_not_found")));

    if (user.getType() != UserType.ASSISTANT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invite.wrong_user_type"));
    }

    AssistantModel assistant =
        assistantRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("assistant.invite.email_not_found")));

    expireStalePendingInviteForAssistant(assistant);

    if (isWithinRejectionCooldown(driver.getId(), assistant.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("assistant.invite.cooldown"));
    }

    if (assistant.getStatus() == AssistantStatus.ACTIVE
        || assistant.getStatus() == AssistantStatus.INACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invite.not_unlinked"));
    }

    if (assistant.getStatus() == AssistantStatus.PENDING) {
      Optional<AssistantInviteModel> pending =
          inviteRepository.findByAssistantIdAndStatus(
              assistant.getId(), AssistantInviteStatus.PENDING);
      if (pending.isPresent()) {
        AssistantInviteModel existing = pending.get();
        expireIfStale(existing);
        if (existing.getStatus() == AssistantInviteStatus.PENDING) {
          if (!existing.getDriver().getId().equals(driver.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, message("assistant.invite.not_unlinked"));
          }
          cancelPendingInvite(existing, false);
        }
      }
    }

    AssistantInviteModel invite = new AssistantInviteModel();
    invite.setDriver(driver);
    invite.setAssistant(assistant);
    invite.setExpiresAt(Instant.now().plus(inviteTtl));
    invite = inviteRepository.save(invite);

    assistant.setStatus(AssistantStatus.PENDING);
    assistantRepository.save(assistant);

    sendInviteEmail(assistant, driver);

    return mapper.toInviteResponse(invite);
  }

  @Transactional
  public void cancelInvite(String callerEmail, String inviteToken) {
    DriverModel driver = resolveDriver(callerEmail);
    AssistantInviteModel invite =
        inviteRepository
            .findByToken(inviteToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("assistant.invite.not_found")));

    if (!invite.getDriver().getId().equals(driver.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, message("assistant.invite.forbidden"));
    }

    expireIfStale(invite);
    if (invite.getStatus() != AssistantInviteStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("assistant.invite.not_pending"));
    }

    cancelPendingInvite(invite, true);
  }

  @Transactional
  public void expireIfStale(AssistantInviteModel invite) {
    if (invite.getStatus() != AssistantInviteStatus.PENDING) {
      return;
    }
    if (!invite.getExpiresAt().isBefore(Instant.now())) {
      return;
    }
    invite.setStatus(AssistantInviteStatus.EXPIRED);
    invite.setRespondedAt(Instant.now());
    inviteRepository.save(invite);

    AssistantModel assistant = invite.getAssistant();
    if (assistant.getStatus() == AssistantStatus.PENDING) {
      assistant.setStatus(AssistantStatus.UNLINKED);
      assistantRepository.save(assistant);
    }
  }

  void expireStalePendingInviteForAssistant(AssistantModel assistant) {
    inviteRepository
        .findByAssistantIdAndStatus(assistant.getId(), AssistantInviteStatus.PENDING)
        .ifPresent(this::expireIfStale);
  }

  private boolean isWithinRejectionCooldown(Long driverId, Long assistantId) {
    Instant since = Instant.now().minus(REJECTION_COOLDOWN);
    return inviteRepository.existsByDriverIdAndAssistantIdAndStatusAndRespondedAtGreaterThanEqual(
        driverId, assistantId, AssistantInviteStatus.REJECTED, since);
  }

  private void cancelPendingInvite(AssistantInviteModel invite, boolean revertAssistant) {
    invite.setStatus(AssistantInviteStatus.CANCELLED);
    invite.setRespondedAt(Instant.now());
    inviteRepository.save(invite);
    if (revertAssistant) {
      AssistantModel assistant = invite.getAssistant();
      if (assistant.getStatus() == AssistantStatus.PENDING) {
        assistant.setStatus(AssistantStatus.UNLINKED);
        assistantRepository.save(assistant);
      }
    }
  }

  private void sendInviteEmail(AssistantModel assistant, DriverModel driver) {
    mail.send(
        assistant.getUser().getEmail(),
        message("assistant.invite.email.subject"),
        "email/assistant-invite",
        Map.of(
            "name",
            assistant.getUser().getName(),
            "driverName",
            driver.getUser().getName(),
            "expiryHours",
            String.valueOf(inviteTtl.toHours())));
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
