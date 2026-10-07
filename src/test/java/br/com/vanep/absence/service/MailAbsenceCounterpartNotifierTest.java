package br.com.vanep.absence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceSource;
import br.com.vanep.absence.model.AbsenceModel;
import br.com.vanep.absence.repository.AbsenceRepository;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class MailAbsenceCounterpartNotifierTest {

  @Mock private MailService mail;
  @Mock private AbsenceRepository absences;
  @Mock private MessageSource messages;

  private MailAbsenceCounterpartNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new MailAbsenceCounterpartNotifier(mail, absences, messages);
    when(messages.getMessage(anyString(), any(), any())).thenReturn("assunto");
  }

  @Test
  void clientReportEmailsTheDriverAndSetsNotifiedAt() {
    AbsenceModel absence = row(AbsenceSource.CLIENT);

    notifier.notifyCounterpart(absence);

    verify(mail)
        .send(
            eq("motorista@vanep.com"),
            eq("assunto"),
            eq("email/absence-client-report"),
            any(Map.class));
    assertThat(absence.getNotifiedAt()).isNotNull();
    verify(absences).save(absence);
  }

  @Test
  void noShowEmailsTheClientAndSetsNotifiedAt() {
    AbsenceModel absence = row(AbsenceSource.DRIVER);

    notifier.notifyCounterpart(absence);

    verify(mail)
        .send(eq("cliente@vanep.com"), eq("assunto"), eq("email/absence-no-show"), any(Map.class));
    assertThat(absence.getNotifiedAt()).isNotNull();
    verify(absences).save(absence);
  }

  @Test
  void mailFailureKeepsNotifiedAtNull() {
    AbsenceModel absence = row(AbsenceSource.CLIENT);
    doThrow(new RuntimeException("smtp down"))
        .when(mail)
        .send(anyString(), anyString(), anyString(), any());

    notifier.notifyCounterpart(absence);

    assertThat(absence.getNotifiedAt()).isNull();
    verify(absences, never()).save(any());
  }

  private AbsenceModel row(AbsenceSource source) {
    UserModel driverUser = new UserModel();
    driverUser.setEmail("motorista@vanep.com");
    driverUser.setName("Carlos");
    DriverModel driver = new DriverModel();
    driver.setUser(driverUser);
    UserModel clientUser = new UserModel();
    clientUser.setEmail("cliente@vanep.com");
    clientUser.setName("Maria");
    ClientModel client = new ClientModel();
    client.setUser(clientUser);
    ClientDriverModel link = new ClientDriverModel();
    link.setDriver(driver);
    link.setClient(client);
    DependentModel dependent = new DependentModel();
    dependent.setName("Aluno");
    AbsenceModel absence = new AbsenceModel();
    absence.setToken("abs-token");
    absence.setSource(source);
    absence.setLeg(AbsenceLeg.OUTBOUND);
    absence.setClientDriver(link);
    absence.setDependent(dependent);
    return absence;
  }
}
