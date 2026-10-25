package br.com.vanep.proposal.service;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.proposal.dto.ProposalResponseDTO;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.event.ServiceRequestEvent;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NegotiationEmailService {

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(TripService.SERVICE_ZONE);

  private final MailService mail;
  private final UserService users;
  private final ServiceRequestService requests;
  private final ProposalService proposals;
  private final ServiceRequestMapper requestMapper;
  private final MessageSource messages;

  public NegotiationEmailService(
      MailService mail,
      UserService users,
      ServiceRequestService requests,
      ProposalService proposals,
      ServiceRequestMapper requestMapper,
      MessageSource messages) {
    this.mail = mail;
    this.users = users;
    this.requests = requests;
    this.proposals = proposals;
    this.requestMapper = requestMapper;
    this.messages = messages;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendRequestSent(ServiceRequestEvent.Sent event) {
    ServiceRequestDriverResponseDTO request =
        requests.findForDriver(event.driverUid(), event.requestToken());
    Map<String, Object> variables = new HashMap<>();
    variables.put("clientFirstName", request.clientFirstName());
    variables.put(
        "dependents",
        joinNames(request.items().stream().map(item -> item.dependentFirstName()).toList()));
    variables.put("desiredStartDate", DATE.format(request.desiredStartDate()));
    variables.put("expiresAt", DATE_TIME.format(request.expiresAt()));
    send(
        event.driverUid(),
        "service_request.sent.email.subject",
        "email/service-request-sent",
        variables);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendRequestDeclined(ServiceRequestEvent.Declined event) {
    ServiceRequestClientResponseDTO request =
        requests.findForClient(event.clientUid(), event.requestToken());
    Map<String, Object> variables = new HashMap<>();
    variables.put("driverName", request.driverName());
    variables.put("reason", request.declineReason());
    send(
        event.clientUid(),
        "service_request.declined.email.subject",
        "email/service-request-declined",
        variables);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendProposalSent(ProposalEvent.Sent event) {
    ProposalResponseDTO proposal =
        proposals.findForClient(event.clientUid(), event.proposalToken());
    ServiceRequestClientResponseDTO request =
        requests.findForClient(event.clientUid(), proposal.serviceRequestToken());
    Map<String, Object> variables = new HashMap<>();
    variables.put("driverName", request.driverName());
    variables.put("version", proposal.version());
    variables.put(
        "dependents",
        joinNames(proposal.items().stream().map(item -> item.dependentName()).toList()));
    variables.put(
        "totalAmount", NumberFormat.getCurrencyInstance(PT_BR).format(proposal.totalAmount()));
    variables.put("installments", proposal.installments());
    variables.put("validUntil", DATE_TIME.format(proposal.validUntil()));
    send(event.clientUid(), "proposal.sent.email.subject", "email/proposal-sent", variables);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendAdjustmentRequested(ProposalEvent.AdjustmentRequested event) {
    ProposalResponseDTO proposal =
        proposals.findForDriver(event.driverUid(), event.proposalToken());
    ProposalResponseDTO.Adjustment adjustment = proposal.adjustments().getLast();
    Map<String, Object> variables = driverVariables(event.driverUid(), proposal);
    variables.put("field", message("proposal.adjustment.field." + adjustment.field().name()));
    variables.put("suggestedValue", adjustment.suggestedValue());
    variables.put("note", adjustment.note());
    send(
        event.driverUid(),
        "proposal.adjustment_requested.email.subject",
        "email/proposal-adjustment-requested",
        variables);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendProposalAccepted(ProposalEvent.Accepted event) {
    ProposalResponseDTO proposal =
        proposals.findForDriver(event.driverUid(), event.proposalToken());
    Map<String, Object> variables = driverVariables(event.driverUid(), proposal);
    variables.put(
        "dependents",
        joinNames(proposal.items().stream().map(item -> item.dependentFirstName()).toList()));
    send(
        event.driverUid(), "proposal.accepted.email.subject", "email/proposal-accepted", variables);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void sendProposalDeclined(ProposalEvent.Declined event) {
    ProposalResponseDTO proposal =
        proposals.findForDriver(event.driverUid(), event.proposalToken());
    Map<String, Object> variables = driverVariables(event.driverUid(), proposal);
    variables.put("reason", proposal.declineReason());
    send(
        event.driverUid(), "proposal.declined.email.subject", "email/proposal-declined", variables);
  }

  Map<String, Object> driverVariables(String driverUid, ProposalResponseDTO proposal) {
    ServiceRequestDriverResponseDTO request =
        requests.findForDriver(driverUid, proposal.serviceRequestToken());
    Map<String, Object> variables = new HashMap<>();
    variables.put("clientFirstName", request.clientFirstName());
    variables.put("version", proposal.version());
    return variables;
  }

  void send(
      String recipientUid, String subjectKey, String template, Map<String, Object> variables) {
    UserModel recipient = users.requireByToken(recipientUid);
    variables.put("name", requestMapper.firstNameOf(recipient.getName()));
    mail.send(recipient.getEmail(), message(subjectKey), template, variables);
  }

  String joinNames(List<String> names) {
    if (names.size() == 1) {
      return names.getFirst();
    }
    return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.getLast();
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
