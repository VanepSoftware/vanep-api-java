package br.com.vanep.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.service.ClientDriverBlockService;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.controller.ProposalControllerFixtures;
import br.com.vanep.proposal.dto.ProposalAdjustmentRequestDTO;
import br.com.vanep.proposal.dto.ProposalDeclineRequestDTO;
import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures;
import br.com.vanep.servicerequest.dto.ServiceRequestDeclineRequestDTO;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.vehicle.model.VehicleModel;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NegotiationEmailServiceTest {

  @Autowired private ApplicationContext context;
  @Autowired private ServiceRequestService requestService;
  @Autowired private ProposalService proposalService;
  @Autowired private ClientDriverBlockService blockService;
  @Autowired private SpringTemplateEngine templateEngine;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private MailService mail;

  private ServiceRequestControllerFixtures fixtures;
  private ProposalControllerFixtures proposalFixtures;
  private ClientModel ana;
  private DriverModel carlos;
  private DependentModel lucas;
  private VehicleModel van;

  @BeforeEach
  void setUp() {
    fixtures = new ServiceRequestControllerFixtures(context);
    proposalFixtures = new ProposalControllerFixtures(context);
    ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    lucas = fixtures.createDependentGoingEveryWeekday(ana, "Lucas Souza", LocalDate.of(2016, 5, 4));
    van = proposalFixtures.createVehicle(carlos, "VAN1A23");
  }

  @Test
  void aNewRequestEmailsTheDriverWithFirstNamesOnly() {
    fixtures.sendRequest(ana, carlos, lucas);

    Email email = onlyEmail();
    assertThat(email.to()).isEqualTo("carlos@vanep.com");
    assertThat(email.subject()).isEqualTo(message("service_request.sent.email.subject"));
    assertThat(email.template()).isEqualTo("email/service-request-sent");
    assertThat(email.html())
        .contains("Carlos", "Ana", "Lucas")
        .doesNotContain("Souza", "2016", "Rua A", "Cambuí", "13015904");
  }

  @Test
  void aDeclinedRequestEmailsTheClientWithTheReason() {
    String request = fixtures.sendRequest(ana, carlos, lucas);
    clearInvocations(mail);

    requestService.decline(
        carlosUid(), request, new ServiceRequestDeclineRequestDTO("Sem vaga à tarde"));

    Email email = onlyEmail();
    assertThat(email.to()).isEqualTo("ana@vanep.com");
    assertThat(email.template()).isEqualTo("email/service-request-declined");
    assertThat(email.html()).contains("Ana", "Carlos Lima", "Sem vaga à tarde");
  }

  @Test
  void everyVersionOfAProposalEmailsTheClient() {
    String request = fixtures.sendRequest(ana, carlos, lucas);
    clearInvocations(mail);

    proposalFixtures.sendProposal(carlos, request, van, lucas);
    proposalFixtures.sendProposal(carlos, request, van, lucas);

    List<Email> emails = emails(2);
    assertThat(emails).extracting(email -> email.to()).containsOnly("ana@vanep.com");
    assertThat(emails).extracting(email -> email.template()).containsOnly("email/proposal-sent");
    assertThat(emails).extracting(email -> email.variables().get("version")).containsExactly(1, 2);
    assertThat(emails.getLast().html()).contains("Carlos Lima", "Lucas Souza", "R$");
  }

  @Test
  void anAdjustmentEmailsTheDriverWithWhatTheClientAsked() {
    String proposal = sentProposal();

    proposalService.requestAdjustment(
        anaUid(),
        proposal,
        new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.PRICE, "320,00", "Cabe?"));

    Email email = onlyEmail();
    assertThat(email.to()).isEqualTo("carlos@vanep.com");
    assertThat(email.template()).isEqualTo("email/proposal-adjustment-requested");
    assertThat(email.html())
        .contains("Ana", message("proposal.adjustment.field.PRICE"), "320,00", "Cabe?")
        .doesNotContain("Souza");
  }

  @Test
  void anAcceptedProposalEmailsTheDriver() {
    String proposal = sentProposal();

    proposalService.accept(anaUid(), proposal);

    Email email = onlyEmail();
    assertThat(email.to()).isEqualTo("carlos@vanep.com");
    assertThat(email.template()).isEqualTo("email/proposal-accepted");
    assertThat(email.html()).contains("Ana", "Lucas").doesNotContain("Souza");
  }

  @Test
  void aDeclinedProposalEmailsTheDriverWithTheReason() {
    String proposal = sentProposal();

    proposalService.decline(anaUid(), proposal, new ProposalDeclineRequestDTO("Muito caro"));

    Email email = onlyEmail();
    assertThat(email.to()).isEqualTo("carlos@vanep.com");
    assertThat(email.template()).isEqualTo("email/proposal-declined");
    assertThat(email.html()).contains("Ana", "Muito caro").doesNotContain("Souza");
  }

  @Test
  void withdrawalsExpirationsAndBlocksSendNothing() {
    String request = fixtures.sendRequest(ana, carlos, lucas);
    String proposal = proposalFixtures.sendProposal(carlos, request, van, lucas);
    clearInvocations(mail);

    proposalService.withdraw(carlosUid(), proposal);
    jdbc.update(
        "update service_request set expires_at = ? where token = ?",
        Timestamp.from(Instant.now().minusSeconds(60)),
        request);
    requestService.expireOverdue(Instant.now(), 10);
    String second = fixtures.sendRequest(ana, carlos, lucas);
    String secondProposal = proposalFixtures.sendProposal(carlos, second, van, lucas);
    clearInvocations(mail);
    jdbc.update(
        "update proposal set valid_until = ? where token = ?",
        Timestamp.from(Instant.now().minusSeconds(60)),
        secondProposal);
    proposalService.expireOverdue(Instant.now(), 10);
    requestService.withdraw(anaUid(), second);
    fixtures.sendRequest(ana, carlos, lucas);
    clearInvocations(mail);
    blockService.block(
        anaUid(), jdbc.queryForObject("select token from client_driver", String.class));

    verify(mail, never()).send(any(), any(), any(), any());
  }

  @Test
  void aRefusedRequestSendsNothing() {
    fixtures.sendRequest(ana, carlos, lucas);
    clearInvocations(mail);

    assertThatThrownBy(() -> fixtures.sendRequest(ana, carlos, lucas))
        .isInstanceOf(ResponseStatusException.class);

    verify(mail, never()).send(any(), any(), any(), any());
  }

  @Test
  void aFailingMailServerDoesNotFailTheProposal() {
    String request = fixtures.sendRequest(ana, carlos, lucas);
    doThrow(new IllegalStateException("SMTP down")).when(mail).send(any(), any(), any(), any());

    String proposal = proposalFixtures.sendProposal(carlos, request, van, lucas);

    assertThat(
            jdbc.queryForObject(
                "select status from proposal where token = ?", String.class, proposal))
        .isEqualTo("SENT");
  }

  private String sentProposal() {
    String request = fixtures.sendRequest(ana, carlos, lucas);
    String proposal = proposalFixtures.sendProposal(carlos, request, van, lucas);
    clearInvocations(mail);
    return proposal;
  }

  private Email onlyEmail() {
    return emails(1).getFirst();
  }

  @SuppressWarnings("unchecked")
  private List<Email> emails(int count) {
    ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> template = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
    verify(mail, times(count))
        .send(to.capture(), subject.capture(), template.capture(), variables.capture());
    return IntStream.range(0, count)
        .mapToObj(
            index ->
                new Email(
                    to.getAllValues().get(index),
                    subject.getAllValues().get(index),
                    template.getAllValues().get(index),
                    variables.getAllValues().get(index),
                    render(
                        template.getAllValues().get(index), variables.getAllValues().get(index))))
        .toList();
  }

  private String anaUid() {
    return ana.getUser().getToken();
  }

  private String carlosUid() {
    return carlos.getUser().getToken();
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  private String render(String template, Map<String, Object> variables) {
    Context context = new Context(Locale.forLanguageTag("pt-BR"));
    context.setVariables(variables);
    return templateEngine.process(template, context);
  }

  private record Email(
      String to, String subject, String template, Map<String, Object> variables, String html) {}
}
