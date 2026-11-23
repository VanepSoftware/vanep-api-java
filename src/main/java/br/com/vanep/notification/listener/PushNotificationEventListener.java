package br.com.vanep.notification.listener;

import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.event.ContractCreatedEvent;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.notification.config.PushNotificationAsyncConfig;
import br.com.vanep.notification.enums.PushNotificationType;
import br.com.vanep.notification.service.PushNotificationService;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PushNotificationEventListener {

  public static final String TYPE_KEY = "type";

  // Runs on a pool thread, outside any request, so the request's fixed pt-BR locale is not there.
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final PushNotificationService push;
  private final UserService users;
  private final ConversationRepository conversations;
  private final ClientDriverRepository links;
  private final DriverRepository drivers;
  private final MessageSource messages;

  public PushNotificationEventListener(
      PushNotificationService push,
      UserService users,
      ConversationRepository conversations,
      ClientDriverRepository links,
      DriverRepository drivers,
      MessageSource messages) {
    this.push = push;
    this.users = users;
    this.conversations = conversations;
    this.links = links;
    this.drivers = drivers;
    this.messages = messages;
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyMessageSent(ConversationMessageSentEvent event) {
    conversations
        .findByToken(event.conversationToken())
        .ifPresent(
            conversation -> {
              boolean fromClient = event.message().sender() == ConversationParticipant.CLIENT;
              UserModel sender =
                  fromClient
                      ? conversation.getClient().getUser()
                      : conversation.getDriver().getUser();
              UserModel recipient =
                  fromClient
                      ? conversation.getDriver().getUser()
                      : conversation.getClient().getUser();
              send(
                  recipient,
                  PushNotificationType.CHAT_MESSAGE,
                  Map.of("conversationToken", event.conversationToken()),
                  sender.getName());
            });
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalSent(ProposalEvent.Sent event) {
    send(event.clientUid(), PushNotificationType.PROPOSAL_RECEIVED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalAccepted(ProposalEvent.Accepted event) {
    send(event.driverUid(), PushNotificationType.PROPOSAL_ACCEPTED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalDeclined(ProposalEvent.Declined event) {
    send(event.driverUid(), PushNotificationType.PROPOSAL_DECLINED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyContractCreated(ContractCreatedEvent event) {
    Map<String, String> data = Map.of("contractToken", event.contractToken());
    links
        .findByToken(event.clientDriverToken())
        .ifPresent(
            link -> {
              send(link.getClient().getUser(), PushNotificationType.CONTRACT_CREATED, data);
              send(link.getDriver().getUser(), PushNotificationType.CONTRACT_CREATED, data);
            });
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyDriverReviewed(DriverReviewEvent event) {
    PushNotificationType type =
        switch (event) {
          case DriverReviewEvent.Approved approved -> PushNotificationType.DRIVER_APPROVED;
          case DriverReviewEvent.Rejected rejected -> PushNotificationType.DRIVER_REJECTED;
        };
    drivers
        .findByToken(event.driverToken())
        .ifPresent(driver -> send(driver.getUser(), type, Map.of()));
  }

  Map<String, String> proposalData(ProposalEvent event) {
    return Map.of("proposalToken", event.proposalToken());
  }

  void send(String recipientUid, PushNotificationType type, Map<String, String> data) {
    send(users.requireByToken(recipientUid), type, data);
  }

  void send(
      UserModel recipient,
      PushNotificationType type,
      Map<String, String> data,
      Object... titleArgs) {
    Map<String, String> payload = new HashMap<>(data);
    payload.put(TYPE_KEY, type.name());
    push.sendToUser(
        recipient.getId(),
        messages.getMessage(type.titleKey(), titleArgs, PT_BR),
        messages.getMessage(type.bodyKey(), null, PT_BR),
        payload);
  }
}
