package br.com.vanep.conversation.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.dto.ConversationCreateRequestDTO;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationService {

  public record OpenedConversation(ConversationResponseDTO conversation, boolean created) {}

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

  // Not transactional on purpose: when a concurrent open wins the unique index, the
  // failed insert must not abort the transaction that re-reads the winner.
  public OpenedConversation openConversation(
      String callerUid, ConversationCreateRequestDTO request) {
    ClientModel client = requireCallerClient(callerUid);
    DriverModel driver =
        drivers
            .findSearchableByToken(request.driverToken())
            .orElseThrow(() -> notFound("conversation.driver.not_found"));
    return conversations
        .findByPair(client.getId(), driver.getId())
        .map(existing -> new OpenedConversation(mapper.toResponse(existing), false))
        .orElseGet(() -> createConversation(client, driver));
  }

  OpenedConversation createConversation(ClientModel client, DriverModel driver) {
    ConversationModel conversation = new ConversationModel();
    conversation.setClient(client);
    conversation.setDriver(driver);
    try {
      return new OpenedConversation(
          mapper.toResponse(conversations.saveAndFlush(conversation)), true);
    } catch (DataIntegrityViolationException concurrentOpen) {
      return conversations
          .findByPair(client.getId(), driver.getId())
          .map(existing -> new OpenedConversation(mapper.toResponse(existing), false))
          .orElseThrow(() -> concurrentOpen);
    }
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

  ClientModel requireCallerClient(String callerUid) {
    UserModel caller = users.requireByToken(callerUid);
    return clients
        .findByUserId(caller.getId())
        .orElseThrow(() -> notFound("conversation.client.not_found"));
  }

  ConversationModel requireConversation(String token) {
    return conversations.findByToken(token).orElseThrow(() -> notFound("conversation.not_found"));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, messageSource.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
