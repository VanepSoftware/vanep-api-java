package br.com.vanep.driver.service;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.shared.document.enums.DocumentRejectionReason;
import br.com.vanep.shared.document.event.DocumentRejectedEvent;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.model.DocumentSubmission;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class DocumentNotificationService {

  private static final Logger log = LoggerFactory.getLogger(DocumentNotificationService.class);
  // The driver reads this e-mail; the admin's request locale must not pick its language.
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final DriverDocumentRepository driverDocuments;
  private final VehicleDocumentRepository vehicleDocuments;
  private final DriverCnhRepository cnhs;
  private final MailService mailService;
  private final MessageSource messages;

  public DocumentNotificationService(
      DriverDocumentRepository driverDocuments,
      VehicleDocumentRepository vehicleDocuments,
      DriverCnhRepository cnhs,
      MailService mailService,
      MessageSource messages) {
    this.driverDocuments = driverDocuments;
    this.vehicleDocuments = vehicleDocuments;
    this.cnhs = cnhs;
    this.mailService = mailService;
    this.messages = messages;
  }

  // A new transaction after the decision's commit: a failed e-mail never undoes the decision,
  // and notifiedAt stays empty, with no retry.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notifyRejection(DocumentRejectedEvent event) {
    Optional<RejectionNotice> notice = findNotice(event);
    if (notice.isEmpty()) {
      log.warn("Rejected {} {} not found to e-mail.", event.kind(), event.token());
      return;
    }
    if (send(notice.get())) {
      notice.get().submission().setNotifiedAt(Instant.now());
    } else {
      log.warn("The rejection of {} {} was not e-mailed.", event.kind(), event.token());
    }
  }

  Optional<RejectionNotice> findNotice(DocumentRejectedEvent event) {
    return switch (event.kind()) {
      case DRIVER_DOCUMENT ->
          driverDocuments
              .findByToken(event.token())
              .map(
                  document ->
                      new RejectionNotice(
                          document,
                          document.getDriver(),
                          message("driver_document.type." + document.getDocumentType().name()),
                          null));
      case VEHICLE_DOCUMENT ->
          vehicleDocuments
              .findByToken(event.token())
              .map(
                  document ->
                      new RejectionNotice(
                          document,
                          document.getVehicle().getDriver(),
                          message("vehicle_document.type." + document.getDocumentType().name()),
                          document.getVehicle().getPlate()));
      case DRIVER_CNH ->
          cnhs.findByToken(event.token())
              .map(
                  cnh ->
                      new RejectionNotice(cnh, cnh.getDriver(), message("driver_cnh.name"), null));
    };
  }

  boolean send(RejectionNotice notice) {
    try {
      DocumentReview review = notice.submission().getReview();
      Map<String, Object> variables = new HashMap<>();
      variables.put("name", notice.driver().getUser().getName());
      variables.put(
          "intro",
          notice.plate() == null
              ? message("email.document_rejected.intro", notice.documentName())
              : message(
                  "email.document_rejected.intro_vehicle", notice.documentName(), notice.plate()));
      if (review.getRejectionReasonCode() == DocumentRejectionReason.OTHER) {
        variables.put("reason", review.getRejectionReason());
      } else {
        variables.put(
            "reason",
            message(
                "document.rejection_reason."
                    + Objects.requireNonNull(review.getRejectionReasonCode()).name()));
        variables.put("details", review.getRejectionReason());
      }
      variables.put("action", message("email.document_rejected.action"));
      return mailService.send(
          notice.driver().getUser().getEmail(),
          message("email.document_rejected.subject"),
          "email/document-rejected",
          variables);
    } catch (RuntimeException failure) {
      log.error("Could not build the rejection e-mail.", failure);
      return false;
    }
  }

  private String message(String key, Object... arguments) {
    return messages.getMessage(key, arguments, PT_BR);
  }

  record RejectionNotice(
      DocumentSubmission submission,
      DriverModel driver,
      String documentName,
      @Nullable String plate) {}
}
