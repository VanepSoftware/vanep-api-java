package br.com.vanep.conversation.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.dto.ConversationMessageCreateRequestDTO;
import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.dto.ConversationResponseDTO;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

  public record Pair(Long clientId, Long driverId) {}

  private final ConversationRepository conversations;
  private final ConversationMessageRepository messages;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final UserService users;
  private final ConversationMapper mapper;
  private final ApplicationEventPublisher events;
  private final MessageSource messageSource;

  public ConversationService(
      ConversationRepository conversations,
      ConversationMessageRepository messages,
      ClientRepository clients,
      DriverRepository drivers,
      UserService users,
      ConversationMapper mapper,
      ApplicationEventPublisher events,
      MessageSource messageSource) {
    this.conversations = conversations;
    this.messages = messages;
    this.clients = clients;
    this.drivers = drivers;
    this.users = users;
    this.mapper = mapper;
    this.events = events;
    this.messageSource = messageSource;
  }

  // Concurrent requests of the pair already serialize on its link, so this insert never races.
  @Transactional
  public ConversationModel findOrCreateForRequest(ClientModel client, DriverModel driver) {
    return conversations
        .findByPair(client.getId(), driver.getId())
        .orElseGet(() -> conversations.saveAndFlush(newConversation(client, driver)));
  }

  ConversationModel newConversation(ClientModel client, DriverModel driver) {
    ConversationModel conversation = new ConversationModel();
    conversation.setClient(client);
    conversation.setDriver(driver);
    return conversation;
  }

  @Transactional(readOnly = true)
  public Optional<Pair> findPairByToken(String token) {
    return conversations
        .findByToken(token)
        .map(
            conversation ->
                new Pair(conversation.getClient().getId(), conversation.getDriver().getId()));
  }

  @Transactional(readOnly = true)
  public Map<Pair, String> findTokensOfPairs(Set<Pair> pairs) {
    if (pairs.isEmpty()) {
      return Map.of();
    }
    Set<Long> clientIds = pairs.stream().map(pair -> pair.clientId()).collect(Collectors.toSet());
    Set<Long> driverIds = pairs.stream().map(pair -> pair.driverId()).collect(Collectors.toSet());
    return conversations.findTokensByClientIdInAndDriverIdIn(clientIds, driverIds).stream()
        .filter(row -> pairs.contains(new Pair(row.getClientId(), row.getDriverId())))
        .collect(
            Collectors.toMap(
                row -> new Pair(row.getClientId(), row.getDriverId()), row -> row.getToken()));
  }

  @Transactional(readOnly = true)
  public List<ConversationResponseDTO> listMyConversations(String callerUid) {
    UserModel caller = users.requireByToken(callerUid);
    return findConversationsOf(caller).stream().map(mapper::toResponse).toList();
  }

  List<ConversationModel> findConversationsOf(UserModel caller) {
    return switch (caller.getType()) {
      case DRIVER ->
          drivers
              .findByUserId(caller.getId())
              .map(driver -> conversations.findByDriverIdMostRecentFirst(driver.getId()))
              .orElse(List.of());
      case CLIENT ->
          clients
              .findByUserId(caller.getId())
              .map(client -> conversations.findByClientIdMostRecentFirst(client.getId()))
              .orElse(List.of());
      default -> List.of();
    };
  }

  @Transactional(readOnly = true)
  public Page<ConversationMessageResponseDTO> listMessages(String token, Pageable pageable) {
    ConversationModel conversation = requireConversation(token);
    Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return messages
        .findByConversationIdNewestFirst(conversation.getId(), unsorted)
        .map(mapper::toMessageResponse);
  }

  @Transactional
  public ConversationMessageResponseDTO sendMessage(
      String callerUid, String token, ConversationMessageCreateRequestDTO request) {
    ConversationModel conversation = requireConversation(token);
    Instant sentAt = Instant.now();
    ConversationMessageModel message = new ConversationMessageModel();
    message.setConversation(conversation);
    message.setSender(resolveSender(conversation, callerUid));
    message.setBody(request.body());
    message.setSentAt(sentAt);
    conversation.setLastMessageAt(sentAt);
    conversations.save(conversation);
    ConversationMessageResponseDTO sent = mapper.toMessageResponse(messages.save(message));
    events.publishEvent(new ConversationMessageSentEvent(conversation.getToken(), sent));
    return sent;
  }

  ConversationParticipant resolveSender(ConversationModel conversation, String callerUid) {
    return callerUid.equals(conversation.getClient().getUser().getToken())
        ? ConversationParticipant.CLIENT
        : ConversationParticipant.DRIVER;
  }

  ConversationModel requireConversation(String token) {
    return conversations.findByToken(token).orElseThrow(() -> notFound("conversation.not_found"));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
