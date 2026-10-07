package br.com.vanep.absence.service;

import br.com.vanep.absence.enums.AbsenceSource;
import br.com.vanep.absence.model.AbsenceModel;
import br.com.vanep.absence.repository.AbsenceRepository;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.trip.service.TripService;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MailAbsenceCounterpartNotifier implements AbsenceCounterpartNotifier {

  private static final Logger log = LoggerFactory.getLogger(MailAbsenceCounterpartNotifier.class);
  private static final DateTimeFormatter STOP_TIME =
      DateTimeFormatter.ofPattern("HH:mm").withZone(TripService.SERVICE_ZONE);

  private final MailService mail;
  private final AbsenceRepository absences;
  private final MessageSource messages;

  public MailAbsenceCounterpartNotifier(
      MailService mail, AbsenceRepository absences, MessageSource messages) {
    this.mail = mail;
    this.absences = absences;
    this.messages = messages;
  }

  @Override
  public void notifyCounterpart(AbsenceModel absence) {
    try {
      if (absence.getSource() == AbsenceSource.CLIENT) {
        mail.send(
            absence.getClientDriver().getDriver().getUser().getEmail(),
            message("absence.notify.client_report"),
            "email/absence-client-report",
            Map.of(
                "name",
                absence.getClientDriver().getDriver().getUser().getName(),
                "dependentName",
                absence.getDependent().getName(),
                "leg",
                absence.getLeg().name()));
      } else {
        mail.send(
            absence.getClientDriver().getClient().getUser().getEmail(),
            message("absence.notify.no_show"),
            "email/absence-no-show",
            Map.of(
                "name",
                absence.getClientDriver().getClient().getUser().getName(),
                "dependentName",
                absence.getDependent().getName(),
                "stopTime",
                STOP_TIME.format(ZonedDateTime.now(TripService.SERVICE_ZONE))));
      }
      absence.setNotifiedAt(Instant.now());
      absences.save(absence);
    } catch (RuntimeException ex) {
      log.error("Failed to notify counterpart for absence {}.", absence.getToken(), ex);
    }
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
