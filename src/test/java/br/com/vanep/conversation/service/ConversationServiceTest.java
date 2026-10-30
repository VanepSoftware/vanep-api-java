package br.com.vanep.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.dto.ConversationMessageCreateRequestDTO;
import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import br.com.vanep.conversation.mapper.ConversationMapper;
import br.com.vanep.conversation.model.ConversationMessageModel;
import br.com.vanep.conversation.model.ConversationModel;
import br.com.vanep.conversation.repository.ConversationMessageRepository;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceTest {

  @Mock private ConversationRepository conversations;
  @Mock private ConversationMessageRepository messages;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private UserService users;
  @Mock private ApplicationEventPublisher events;
  @Mock private MessageSource messageSource;

  private ConversationService service;
  private ClientModel maria;
  private DriverModel carlos;
  private ConversationModel conversation;

  @BeforeEach
  void setUp() {
    service =
        new ConversationService(
            conversations,
            messages,
            clients,
            drivers,
            users,
            new ConversationMapper(),
            events,
            messageSource);
    when(messageSource.getMessage(anyString(), any(), any()))
        .thenAnswer(call -> call.getArgument(0));

    maria = new ClientModel();
    maria.setId(1L);
    maria.setUser(user(10L, "maria-uid", "Maria"));
    maria.getUser().setType(UserType.CLIENT);
    carlos = new DriverModel();
    carlos.setId(2L);
    carlos.setToken("carlos-token");
    carlos.setUser(user(20L, "carlos-uid", "Carlos"));
    carlos.getUser().setType(UserType.DRIVER);
    conversation = new ConversationModel();
    conversation.setId(100L);
    conversation.setToken("conversation-token");
    conversation.setClient(maria);
    conversation.setDriver(carlos);

    when(users.requireByToken("maria-uid")).thenReturn(maria.getUser());
    when(users.requireByToken("carlos-uid")).thenReturn(carlos.getUser());
    when(clients.findByUserId(10L)).thenReturn(Optional.of(maria));
    when(drivers.findByUserId(20L)).thenReturn(Optional.of(carlos));
    when(conversations.findByToken("conversation-token")).thenReturn(Optional.of(conversation));
  }

  @Test
  void aRequestOpensTheConversationOfAPairThatHasNone() {
    when(conversations.findByPair(1L, 2L)).thenReturn(Optional.empty());
    when(conversations.saveAndFlush(any())).thenReturn(conversation);

    ConversationModel opened = service.findOrCreateForRequest(maria, carlos);

    assertThat(opened).isSameAs(conversation);
    ArgumentCaptor<ConversationModel> saved = ArgumentCaptor.forClass(ConversationModel.class);
    verify(conversations).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getClient()).isSameAs(maria);
    assertThat(saved.getValue().getDriver()).isSameAs(carlos);
  }

  @Test
  void aRequestKeepsTheConversationThePairAlreadyHas() {
    when(conversations.findByPair(1L, 2L)).thenReturn(Optional.of(conversation));

    assertThat(service.findOrCreateForRequest(maria, carlos)).isSameAs(conversation);
    verify(conversations, never()).saveAndFlush(any());
  }

  @Test
  void findsTheConversationTokensOfOnlyTheAskedPairsInOneQuery() {
    when(conversations.findTokensByClientIdInAndDriverIdIn(Set.of(1L, 3L), Set.of(2L, 4L)))
        .thenReturn(
            List.of(
                new PairTokenRow(1L, 2L, "maria-carlos"),
                new PairTokenRow(1L, 4L, "maria-helena"),
                new PairTokenRow(3L, 4L, "bruno-helena")));

    Map<ConversationService.Pair, String> tokens =
        service.findTokensOfPairs(
            Set.of(new ConversationService.Pair(1L, 2L), new ConversationService.Pair(3L, 4L)));

    assertThat(tokens)
        .containsOnly(
            entry(new ConversationService.Pair(1L, 2L), "maria-carlos"),
            entry(new ConversationService.Pair(3L, 4L), "bruno-helena"));
  }

  @Test
  void noPairsNeedNoQuery() {
    assertThat(service.findTokensOfPairs(Set.of())).isEmpty();
    verify(conversations, never()).findTokensByClientIdInAndDriverIdIn(any(), any());
  }

  @Test
  void findsThePairOfAConversation() {
    assertThat(service.findPairByToken("conversation-token"))
        .contains(new ConversationService.Pair(1L, 2L));
  }

  @Test
  void anUnknownConversationHasNoPair() {
    when(conversations.findByToken("unknown")).thenReturn(Optional.empty());

    assertThat(service.findPairByToken("unknown")).isEmpty();
  }

  @Test
  void theCallerDriverListsTheirConversations() {
    when(conversations.findByDriverIdMostRecentFirst(2L)).thenReturn(List.of(conversation));

    assertThat(service.listMyConversations("carlos-uid"))
        .extracting(found -> found.clientName())
        .containsExactly("Maria");
  }

  @Test
  void theCallerClientListsTheirConversations() {
    when(conversations.findByClientIdMostRecentFirst(1L)).thenReturn(List.of(conversation));

    assertThat(service.listMyConversations("maria-uid"))
        .extracting(found -> found.driverName())
        .containsExactly("Carlos");
  }

  @Test
  void aCallerWithoutTheProfileOfTheirTypeHasNoConversations() {
    when(drivers.findByUserId(20L)).thenReturn(Optional.empty());

    assertThat(service.listMyConversations("carlos-uid")).isEmpty();
  }

  @Test
  void anAdminHasNoConversationsOfTheirOwn() {
    UserModel admin = user(30L, "admin-uid", "Admin");
    admin.setType(UserType.ADMIN);
    when(users.requireByToken("admin-uid")).thenReturn(admin);

    assertThat(service.listMyConversations("admin-uid")).isEmpty();
  }

  @Test
  void theClientSendsAsClientAndTheConversationMovesUp() {
    when(messages.save(any())).thenAnswer(call -> withToken(call.getArgument(0)));

    ConversationMessageResponseDTO sent =
        service.sendMessage(
            "maria-uid",
            "conversation-token",
            new ConversationMessageCreateRequestDTO("Tem vaga para a manhã?"));

    assertThat(sent.sender()).isEqualTo(ConversationParticipant.CLIENT);
    assertThat(sent.body()).isEqualTo("Tem vaga para a manhã?");
    assertThat(conversation.getLastMessageAt()).isEqualTo(sent.sentAt());
  }

  @Test
  void theDriverSendsAsDriver() {
    when(messages.save(any())).thenAnswer(call -> withToken(call.getArgument(0)));

    ConversationMessageResponseDTO sent =
        service.sendMessage(
            "carlos-uid", "conversation-token", new ConversationMessageCreateRequestDTO("Tenho."));

    assertThat(sent.sender()).isEqualTo(ConversationParticipant.DRIVER);
  }

  @Test
  void sendingPublishesTheStoredMessage() {
    when(messages.save(any())).thenAnswer(call -> withToken(call.getArgument(0)));

    ConversationMessageResponseDTO sent =
        service.sendMessage(
            "maria-uid", "conversation-token", new ConversationMessageCreateRequestDTO("Oi"));

    ArgumentCaptor<ConversationMessageSentEvent> published =
        ArgumentCaptor.forClass(ConversationMessageSentEvent.class);
    verify(events).publishEvent(published.capture());
    assertThat(published.getValue().conversationToken()).isEqualTo("conversation-token");
    assertThat(published.getValue().message()).isEqualTo(sent);
  }

  @Test
  void anUnknownConversationIsNotFound() {
    when(conversations.findByToken("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.sendMessage(
                    "maria-uid", "unknown", new ConversationMessageCreateRequestDTO("Oi")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(events, never()).publishEvent(any());
  }

  private ConversationMessageModel withToken(ConversationMessageModel message) {
    message.setToken("message-token");
    return message;
  }

  private record PairTokenRow(Long clientId, Long driverId, String token)
      implements ConversationRepository.PairToken {
    @Override
    public Long getClientId() {
      return clientId;
    }

    @Override
    public Long getDriverId() {
      return driverId;
    }

    @Override
    public String getToken() {
      return token;
    }
  }

  private UserModel user(Long id, String token, String name) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(token);
    user.setName(name);
    return user;
  }
}
