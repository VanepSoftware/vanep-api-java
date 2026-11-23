package br.com.vanep.notification.listener;

import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.event.ContractCreatedEvent;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.notification.config.PushNotificationAsyncConfig;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.service.NotificationService;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

  // Runs on a pool thread, outside any request, so the request's fixed pt-BR locale is not there.
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final NotificationService notifications;
  private final UserService users;
  private final ConversationRepository conversations;
  private final ClientDriverRepository links;
  private final DriverRepository drivers;
  private final MessageSource messages;

  public NotificationEventListener(
      NotificationService notifications,
      UserService users,
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
                  NotificationType.CHAT_MESSAGE,
                  Map.of("conversationToken", event.conversationToken()),
                  sender.getName());
            });
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalSent(ProposalEvent.Sent event) {
    send(event.clientUid(), NotificationType.PROPOSAL_RECEIVED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalAccepted(ProposalEvent.Accepted event) {
    send(event.driverUid(), NotificationType.PROPOSAL_ACCEPTED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyProposalDeclined(ProposalEvent.Declined event) {
    send(event.driverUid(), NotificationType.PROPOSAL_DECLINED, proposalData(event));
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyContractCreated(ContractCreatedEvent event) {
    Map<String, String> data = Map.of("contractToken", event.contractToken());
    links
        .findByToken(event.clientDriverToken())
        .ifPresent(
            link -> {
              send(link.getClient().getUser(), NotificationType.CONTRACT_CREATED, data);
              send(link.getDriver().getUser(), NotificationType.CONTRACT_CREATED, data);
            });
  }

  @Async(PushNotificationAsyncConfig.EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void notifyDriverReviewed(DriverReviewEvent event) {
    NotificationType type =
        switch (event) {
          case DriverReviewEvent.Approved approved -> NotificationType.DRIVER_APPROVED;
          case DriverReviewEvent.Rejected rejected -> NotificationType.DRIVER_REJECTED;
        };
    drivers
        .findByToken(event.driverToken())
        .ifPresent(driver -> send(driver.getUser(), type, Map.of()));
  }

  Map<String, String> proposalData(ProposalEvent event) {
    return Map.of("proposalToken", event.proposalToken());
  }

  void send(String recipientUid, NotificationType type, Map<String, String> data) {
    send(users.requireByToken(recipientUid), type, data);
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
}
