package br.com.vanep.assistant.service;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.repository.DriverLinkCodeRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DriverLinkCodeConsumer {

  private final DriverLinkCodeRepository linkCodes;
  private final AssistantRepository assistants;
  private final MessageSource messages;

  public DriverLinkCodeConsumer(
      DriverLinkCodeRepository linkCodes, AssistantRepository assistants, MessageSource messages) {
    this.linkCodes = linkCodes;
    this.assistants = assistants;
    this.messages = messages;
  }

  public boolean isActiveCode(String plaintextCode) {
    if (!StringUtils.hasText(plaintextCode)) {
      return false;
    }
    return linkCodes
        .findByCodeHash(SecureTokens.hash(plaintextCode.trim()))
        .filter(code -> code.getStatus() == DriverLinkCodeStatus.ACTIVE)
        .filter(code -> code.getExpiresAt().isAfter(Instant.now()))
        .isPresent();
  }

  @Transactional
  public DriverModel consumeAndActivate(AssistantModel assistant, String plaintextCode) {
    if (!StringUtils.hasText(plaintextCode)) {
      throw new InvalidDriverLinkCodeException(message("driver_link_code.invalid_or_expired"));
    }
    String codeHash = SecureTokens.hash(plaintextCode.trim());
    Instant now = Instant.now();
    int updated = linkCodes.consumeIfActive(codeHash, assistant.getId(), now);
    if (updated == 0) {
      throw new InvalidDriverLinkCodeException(message("driver_link_code.invalid_or_expired"));
    }
    DriverLinkCodeModel consumed =
        linkCodes
            .findByCodeHash(codeHash)
            .orElseThrow(
                () ->
                    new InvalidDriverLinkCodeException(
                        message("driver_link_code.invalid_or_expired")));
    DriverModel driver = consumed.getDriver();
    assistant.setDriver(driver);
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setActivatedAt(now);
    assistants.save(assistant);
    return driver;
  }

  private String message(String key) {
    Locale locale = LocaleContextHolder.getLocale();
    return messages.getMessage(key, null, locale);
  }

  public String messageForInvalidCode() {
    return message("driver_link_code.invalid_or_expired");
  }
}
