package br.com.vanep.driver.service;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.model.DriverModel;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class DriverNotificationService {

  private final MailService mailService;
  private final MessageSource messages;

  public DriverNotificationService(MailService mailService, MessageSource messages) {
    this.mailService = mailService;
    this.messages = messages;
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

  public void notifyRejection(DriverModel driver, String reason) {
    Objects.requireNonNull(driver, "driver must not be null");
    Objects.requireNonNull(driver.getUser(), "driver user must not be null");

    String name = driver.getUser().getName() != null ? driver.getUser().getName() : "";
    String safeReason = reason != null ? reason : "";

    mailService.send(
        driver.getUser().getEmail(),
        message("driver.rejection.email.subject"),
        "email/driver-rejected",
        Map.of("name", name, "reason", safeReason));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
