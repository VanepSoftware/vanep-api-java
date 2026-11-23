package br.com.vanep.notification.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.event.ContractCreatedEvent;
import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import br.com.vanep.conversation.model.ConversationModel;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.notification.service.PushNotificationService;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class PushNotificationEventListenerTest {

  @Mock private PushNotificationService push;
  @Mock private UserService users;
  @Mock private ConversationRepository conversations;
  @Mock private ClientDriverRepository links;
  @Mock private DriverRepository drivers;

  private PushNotificationEventListener listener;
  private UserModel maria;
  private UserModel carlos;
  private ClientModel mariaClient;
  private DriverModel carlosDriver;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    listener =
        new PushNotificationEventListener(push, users, conversations, links, drivers, messages);

    maria = user(1L, "maria-uid", "Maria");
    carlos = user(2L, "carlos-uid", "Carlos");
    mariaClient = new ClientModel();
    mariaClient.setUser(maria);
    carlosDriver = new DriverModel();
    carlosDriver.setUser(carlos);
  }

  @Test
  void aClientMessageNotifiesTheDriverWithTheSendersName() {
    when(conversations.findByToken("conv-1")).thenReturn(Optional.of(conversation()));

    listener.notifyMessageSent(messageSent(ConversationParticipant.CLIENT));

    verify(push)
        .sendToUser(
            2L,
            "Nova mensagem de Maria",
            "Toque para abrir a conversa.",
            Map.of("type", "CHAT_MESSAGE", "conversationToken", "conv-1"));
  }

  @Test
  void aDriverMessageNotifiesTheClient() {
    when(conversations.findByToken("conv-1")).thenReturn(Optional.of(conversation()));

    listener.notifyMessageSent(messageSent(ConversationParticipant.DRIVER));

    verify(push)
        .sendToUser(
            1L,
            "Nova mensagem de Carlos",
            "Toque para abrir a conversa.",
            Map.of("type", "CHAT_MESSAGE", "conversationToken", "conv-1"));
  }

  @Test
  void aMessageOfAVanishedConversationNotifiesNoOne() {
    when(conversations.findByToken("conv-1")).thenReturn(Optional.empty());

    listener.notifyMessageSent(messageSent(ConversationParticipant.CLIENT));

    verify(push, never()).sendToUser(anyLong(), any(), any(), any());
  }

  @Test
  void aSentProposalNotifiesTheClient() {
    when(users.requireByToken("maria-uid")).thenReturn(maria);

    listener.notifyProposalSent(new ProposalEvent.Sent("prop-1", "maria-uid"));

    verify(push)
        .sendToUser(
            1L,
            "Nova proposta",
            "Você recebeu uma proposta de transporte. Toque para ver os detalhes.",
            Map.of("type", "PROPOSAL_RECEIVED", "proposalToken", "prop-1"));
  }

  @Test
  void anAcceptedProposalNotifiesTheDriver() {
    when(users.requireByToken("carlos-uid")).thenReturn(carlos);

    listener.notifyProposalAccepted(new ProposalEvent.Accepted("prop-1", "carlos-uid"));

    verify(push)
        .sendToUser(
            2L,
            "Proposta aceita",
            "O responsável aceitou a sua proposta.",
            Map.of("type", "PROPOSAL_ACCEPTED", "proposalToken", "prop-1"));
  }

  @Test
  void aDeclinedProposalNotifiesTheDriver() {
    when(users.requireByToken("carlos-uid")).thenReturn(carlos);

    listener.notifyProposalDeclined(new ProposalEvent.Declined("prop-1", "carlos-uid"));

    verify(push)
        .sendToUser(
            2L,
            "Proposta recusada",
            "O responsável recusou a sua proposta.",
            Map.of("type", "PROPOSAL_DECLINED", "proposalToken", "prop-1"));
  }

  @Test
  void aCreatedContractNotifiesBothParties() {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(mariaClient);
    link.setDriver(carlosDriver);
    when(links.findByToken("link-1")).thenReturn(Optional.of(link));

    listener.notifyContractCreated(new ContractCreatedEvent("contract-1", "link-1"));

    Map<String, String> data = Map.of("type", "CONTRACT_CREATED", "contractToken", "contract-1");
    String body = "Um novo contrato foi criado. Toque para ver os detalhes.";
    verify(push).sendToUser(1L, "Contrato criado", body, data);
    verify(push).sendToUser(2L, "Contrato criado", body, data);
  }

  @Test
  void anApprovedDriverIsNotified() {
    when(drivers.findByToken("driver-1")).thenReturn(Optional.of(carlosDriver));

    listener.notifyDriverReviewed(new DriverReviewEvent.Approved("driver-1"));

    verify(push)
        .sendToUser(
            2L,
            "Cadastro aprovado",
            "Seu cadastro de motorista foi aprovado. Você já pode receber pedidos.",
            Map.of("type", "DRIVER_APPROVED"));
  }

  @Test
  void aRejectedDriverIsNotified() {
    when(drivers.findByToken("driver-1")).thenReturn(Optional.of(carlosDriver));

    listener.notifyDriverReviewed(new DriverReviewEvent.Rejected("driver-1"));

    verify(push)
        .sendToUser(
            2L,
            "Cadastro recusado",
            "Seu cadastro de motorista foi recusado. Toque para ver o motivo.",
            Map.of("type", "DRIVER_REJECTED"));
  }

  private ConversationModel conversation() {
    ConversationModel conversation = new ConversationModel();
    conversation.setToken("conv-1");
    conversation.setClient(mariaClient);
    conversation.setDriver(carlosDriver);
    return conversation;
  }

  private ConversationMessageSentEvent messageSent(ConversationParticipant sender) {
    return new ConversationMessageSentEvent(
        "conv-1", new ConversationMessageResponseDTO("msg-1", "Oi", sender, Instant.now()));
  }

  private UserModel user(Long id, String uid, String name) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(uid);
    user.setName(name);
    return user;
  }
}
