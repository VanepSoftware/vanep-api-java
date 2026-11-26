package br.com.vanep.notification.listener;

import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.event.ContractCreatedEvent;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.service.NotificationService;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// BEFORE_COMMIT ties the notification and its push job to the business change, but an exception
// here rolls that change back: lookups never throw, a missing entity only skips the notification.
@Component
public class NotificationEventListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

  // Notifications are rendered once and stored as sent; they are always pt-BR.
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final NotificationService notifications;
  private final UserRepository users;
  private final ConversationRepository conversations;
  private final ClientDriverRepository links;
  private final DriverRepository drivers;
  private final MessageSource messages;

  public NotificationEventListener(
      NotificationService notifications,
      UserRepository users,
      ConversationRepository conversations,
      ClientDriverRepository links,
      DriverRepository drivers,
      MessageSource messages) {
    this.notifications = notifications;
    this.users = users;
    this.conversations = conversations;
    this.links = links;
    this.drivers = drivers;
    this.messages = messages;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyMessageSent(ConversationMessageSentEvent event) {
    conversations
        .findByToken(event.conversationToken())
        .ifPresentOrElse(
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
                  NotificationType.CHAT_MESSAGE,
                  Map.of("conversationToken", event.conversationToken()),
                  sender.getName());
            },
            () -> logSkipped("conversation", event.conversationToken()));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyProposalSent(ProposalEvent.Sent event) {
    send(event.clientUid(), NotificationType.PROPOSAL_RECEIVED, proposalData(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyProposalAccepted(ProposalEvent.Accepted event) {
    send(event.driverUid(), NotificationType.PROPOSAL_ACCEPTED, proposalData(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyProposalDeclined(ProposalEvent.Declined event) {
    send(event.driverUid(), NotificationType.PROPOSAL_DECLINED, proposalData(event));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyContractCreated(ContractCreatedEvent event) {
    Map<String, String> data = Map.of("contractToken", event.contractToken());
    links
        .findByToken(event.clientDriverToken())
        .ifPresentOrElse(
            link -> {
              send(link.getClient().getUser(), NotificationType.CONTRACT_CREATED, data);
              send(link.getDriver().getUser(), NotificationType.CONTRACT_CREATED, data);
            },
            () -> logSkipped("client-driver link", event.clientDriverToken()));
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void notifyDriverReviewed(DriverReviewEvent event) {
    NotificationType type =
        switch (event) {
          case DriverReviewEvent.Approved approved -> NotificationType.DRIVER_APPROVED;
          case DriverReviewEvent.Rejected rejected -> NotificationType.DRIVER_REJECTED;
        };
    drivers
        .findByToken(event.driverToken())
        .ifPresentOrElse(
            driver -> send(driver.getUser(), type, Map.of()),
            () -> logSkipped("driver", event.driverToken()));
  }

  Map<String, String> proposalData(ProposalEvent event) {
    return Map.of("proposalToken", event.proposalToken());
  }

  void send(String recipientUid, NotificationType type, Map<String, String> data) {
    users
        .findByToken(recipientUid)
        .ifPresentOrElse(
            recipient -> send(recipient, type, data), () -> logSkipped("user", recipientUid));
  }

  void send(
      UserModel recipient, NotificationType type, Map<String, String> data, Object... titleArgs) {
    notifications.notifyUser(
        recipient.getId(),
        type,
        messages.getMessage(type.titleKey(), titleArgs, PT_BR),
        messages.getMessage(type.bodyKey(), null, PT_BR),
        data);
  }

  static void logSkipped(String entity, String token) {
    log.warn("No notification sent: {} {} not found.", entity, token);
  }
}
