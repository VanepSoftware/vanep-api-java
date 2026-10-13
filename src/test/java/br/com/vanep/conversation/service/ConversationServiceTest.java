package br.com.vanep.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.dto.ConversationCreateRequestDTO;
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
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    carlos = new DriverModel();
    carlos.setId(2L);
    carlos.setToken("carlos-token");
    carlos.setUser(user(20L, "carlos-uid", "Carlos"));
    conversation = new ConversationModel();
    conversation.setId(100L);
    conversation.setToken("conversation-token");
    conversation.setClient(maria);
    conversation.setDriver(carlos);

    when(users.requireByToken("maria-uid")).thenReturn(maria.getUser());
    when(users.requireByToken("carlos-uid")).thenReturn(carlos.getUser());
    when(clients.findByUserId(10L)).thenReturn(Optional.of(maria));
    when(drivers.findByUserId(20L)).thenReturn(Optional.of(carlos));
    when(drivers.findSearchableByToken("carlos-token")).thenReturn(Optional.of(carlos));
    when(conversations.findByToken("conversation-token")).thenReturn(Optional.of(conversation));
  }

  @Test
  void opensANewConversationWhenThePairHasNone() {
    when(conversations.findByPair(1L, 2L)).thenReturn(Optional.empty());
    when(conversations.saveAndFlush(any())).thenReturn(conversation);

    ConversationService.OpenedConversation opened =
        service.openConversation("maria-uid", new ConversationCreateRequestDTO("carlos-token"));

    assertThat(opened.created()).isTrue();
    assertThat(opened.conversation().token()).isEqualTo("conversation-token");
  }

  @Test
  void resumesTheExistingConversationOfThePair() {
    when(conversations.findByPair(1L, 2L)).thenReturn(Optional.of(conversation));

    ConversationService.OpenedConversation opened =
        service.openConversation("maria-uid", new ConversationCreateRequestDTO("carlos-token"));

    assertThat(opened.created()).isFalse();
    assertThat(opened.conversation().token()).isEqualTo("conversation-token");
    verify(conversations, never()).saveAndFlush(any());
  }

  @Test
  void resumesTheConversationCreatedByAConcurrentOpen() {
    when(conversations.findByPair(1L, 2L))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(conversation));
    when(conversations.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("conversation_pair_active_key"));

    ConversationService.OpenedConversation opened =
        service.openConversation("maria-uid", new ConversationCreateRequestDTO("carlos-token"));

    assertThat(opened.created()).isFalse();
    assertThat(opened.conversation().token()).isEqualTo("conversation-token");
  }

  @Test
  void refusesADriverThatIsNotSearchable() {
    when(drivers.findSearchableByToken("hidden")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.openConversation("maria-uid", new ConversationCreateRequestDTO("hidden")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(error.getReason()).isEqualTo("conversation.driver.not_found");
            });
  }

  @Test
  void aUserWithoutDriverProfileHasNoDriverConversations() {
    when(drivers.findByUserId(10L)).thenReturn(Optional.empty());

    assertThat(service.listDriverConversations("maria-uid")).isEmpty();
  }

  @Test
  void listsTheConversationsOfTheCallerDriver() {
    when(conversations.findByDriverIdMostRecentFirst(2L)).thenReturn(List.of(conversation));

    assertThat(service.listDriverConversations("carlos-uid"))
        .extracting(found -> found.clientName())
        .containsExactly("Maria");
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

  private UserModel user(Long id, String token, String name) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(token);
    user.setName(name);
    return user;
  }
}
