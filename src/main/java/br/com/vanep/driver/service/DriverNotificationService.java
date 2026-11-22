package br.com.vanep.driver.service;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.enums.DriverRejectionReason;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.model.DriverModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class DriverNotificationService {

  private static final Logger log = LoggerFactory.getLogger(DriverNotificationService.class);

  private final DriverRepository drivers;
  private final MailService mailService;
  private final MessageSource messages;

  public DriverNotificationService(
      DriverRepository drivers, MailService mailService, MessageSource messages) {
    this.drivers = drivers;
    this.mailService = mailService;
    this.messages = messages;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendApproval(DriverReviewEvent.Approved event) {
    drivers
        .findByToken(event.driverToken())
        .ifPresentOrElse(
            driver -> notifyApproval(driver), () -> logNotFound("approval", event.driverToken()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendRejection(DriverReviewEvent.Rejected event) {
    drivers
        .findByToken(event.driverToken())
        .ifPresentOrElse(
            driver -> notifyRejection(driver), () -> logNotFound("rejection", event.driverToken()));
  }

  static void logNotFound(String decision, String driverToken) {
    log.warn("Driver {} not found to e-mail the {}.", driverToken, decision);
  }

  public void notifyApproval(DriverModel driver) {
    Objects.requireNonNull(driver, "driver must not be null");
    Objects.requireNonNull(driver.getUser(), "driver user must not be null");

    String name = driver.getUser().getName() != null ? driver.getUser().getName() : "";

    mailService.send(
        driver.getUser().getEmail(),
        message("driver.approval.email.subject"),
        "email/driver-approved",
        Map.of("name", name));
  }

  public void notifyRejection(DriverModel driver) {
    Objects.requireNonNull(driver, "driver must not be null");
    Objects.requireNonNull(driver.getUser(), "driver user must not be null");

    Map<String, Object> variables = new HashMap<>();
    variables.put("name", driver.getUser().getName() != null ? driver.getUser().getName() : "");
    DriverRejectionReason code = driver.getRejectionReasonCode();
    String written = driver.getRejectionReason();
    if (code == null || code == DriverRejectionReason.OTHER) {
      variables.put("reason", written != null ? written : "");
    } else {
      variables.put("reason", message("driver.rejection_reason." + code.name()));
      variables.put("details", written);
    }

    mailService.send(
        driver.getUser().getEmail(),
        message("driver.rejection.email.subject"),
        "email/driver-rejected",
        variables);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
