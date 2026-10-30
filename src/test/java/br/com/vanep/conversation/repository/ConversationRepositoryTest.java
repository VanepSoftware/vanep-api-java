package br.com.vanep.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.model.ConversationMessageModel;
import br.com.vanep.conversation.model.ConversationModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConversationRepositoryTest {

  @Autowired private ConversationRepository conversations;
  @Autowired private ConversationMessageRepository messages;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private ClientModel maria;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    maria = createClient("Maria", "maria@vanep.com", "11144477735");
    carlos = createDriver("Carlos", "carlos@vanep.com", "52998224725");
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    ConversationModel saved = conversations.save(newConversation(maria, carlos));

    assertThat(saved.getToken()).isNotBlank().doesNotContain("-");
    assertThat(conversations.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void findsTheConversationByPair() {
    ConversationModel saved = conversations.save(newConversation(maria, carlos));

    assertThat(conversations.findByPair(maria.getId(), carlos.getId()))
        .get()
        .extracting(conversation -> conversation.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void driverConversationsHoldOnlyTheirOwnMostRecentMessageFirst() {
    ClientModel bruno = createClient("Bruno", "bruno@vanep.com", "12345678909");
    ClientModel julia = createClient("Julia", "julia@vanep.com", "98765432100");
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ConversationModel older = newConversation(maria, carlos);
    older.setLastMessageAt(Instant.parse("2020-01-01T09:00:00Z"));
    ConversationModel newer = newConversation(bruno, carlos);
    newer.setLastMessageAt(Instant.parse("2020-01-01T10:00:00Z"));
    conversations.saveAll(List.of(older, newer));
    ConversationModel withoutMessages = conversations.save(newConversation(julia, carlos));
    conversations.save(newConversation(maria, helena));

    List<ConversationModel> found = conversations.findByDriverIdMostRecentFirst(carlos.getId());

    assertThat(found)
        .extracting(conversation -> conversation.getId())
        .containsExactly(withoutMessages.getId(), newer.getId(), older.getId());
  }

  @Test
  void clientConversationsHoldOnlyTheirOwnMostRecentMessageFirst() {
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ClientModel bruno = createClient("Bruno", "bruno@vanep.com", "12345678909");
    ConversationModel older = newConversation(maria, carlos);
    older.setLastMessageAt(Instant.parse("2020-01-01T09:00:00Z"));
    ConversationModel newer = newConversation(maria, helena);
    newer.setLastMessageAt(Instant.parse("2020-01-01T10:00:00Z"));
    conversations.saveAll(List.of(older, newer));
    conversations.save(newConversation(bruno, carlos));

    List<ConversationModel> found = conversations.findByClientIdMostRecentFirst(maria.getId());

    assertThat(found)
        .extracting(conversation -> conversation.getId())
        .containsExactly(newer.getId(), older.getId());
  }

  @Test
  void resolvesTheUserTokensOfBothParties() {
    ConversationModel saved = conversations.save(newConversation(maria, carlos));

    assertThat(conversations.findClientUserTokenByToken(saved.getToken()))
        .contains(maria.getUser().getToken());
    assertThat(conversations.findDriverUserTokenByToken(saved.getToken()))
        .contains(carlos.getUser().getToken());
    assertThat(conversations.findClientUserTokenByToken("unknown")).isEmpty();
  }

  @Test
  void softDeletedConversationIsAbsentFromQueries() {
    ConversationModel saved = conversations.save(newConversation(maria, carlos));

    conversations.delete(saved);

    assertThat(conversations.findByToken(saved.getToken())).isEmpty();
    assertThat(conversations.findByPair(maria.getId(), carlos.getId())).isEmpty();
    assertThat(conversations.findClientUserTokenByToken(saved.getToken())).isEmpty();
  }

  @Test
  void findsTheTokensOfThePartiesConversationsLeavingOutRemovedOnes() {
    ClientModel bruno = createClient("Bruno", "bruno@vanep.com", "12345678909");
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ConversationModel mariaCarlos = conversations.save(newConversation(maria, carlos));
    ConversationModel brunoHelena = conversations.save(newConversation(bruno, helena));
    conversations.delete(conversations.save(newConversation(maria, helena)));
    conversations.save(
        newConversation(createClient("Julia", "julia@vanep.com", "98765432100"), carlos));

    List<ConversationRepository.PairToken> found =
        conversations.findTokensByClientIdInAndDriverIdIn(
            List.of(maria.getId(), bruno.getId()), List.of(carlos.getId(), helena.getId()));

    assertThat(found)
        .extracting(row -> row.getClientId(), row -> row.getDriverId(), row -> row.getToken())
        .containsExactlyInAnyOrder(
            tuple(maria.getId(), carlos.getId(), mariaCarlos.getToken()),
            tuple(bruno.getId(), helena.getId(), brunoHelena.getToken()));
  }

  @Test
  void historyIsPaginatedNewestFirst() {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));
    Instant start = Instant.parse("2026-10-12T09:00:00Z");
    for (int minute = 0; minute < 5; minute++) {
      messages.save(newMessage(conversation, "message " + minute, start.plusSeconds(60L * minute)));
    }

    Page<ConversationMessageModel> page =
        messages.findByConversationIdNewestFirst(conversation.getId(), PageRequest.of(0, 3));

    assertThat(page.getContent())
        .extracting(message -> message.getBody())
        .containsExactly("message 4", "message 3", "message 2");
    assertThat(page.getTotalElements()).isEqualTo(5);
    assertThat(page.hasNext()).isTrue();
  }

  @Test
  void historyHoldsOnlyTheMessagesOfThatConversation() {
    ConversationModel withCarlos = conversations.save(newConversation(maria, carlos));
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ConversationModel withHelena = conversations.save(newConversation(maria, helena));
    messages.save(newMessage(withCarlos, "para o Carlos", Instant.now()));
    messages.save(newMessage(withHelena, "para a Helena", Instant.now()));

    Page<ConversationMessageModel> page =
        messages.findByConversationIdNewestFirst(withCarlos.getId(), PageRequest.of(0, 30));

    assertThat(page.getContent())
        .extracting(message -> message.getBody())
        .containsExactly("para o Carlos");
  }

  @Test
  void theBodyIsStoredEncryptedAndReadBackAsText() {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));
    ConversationMessageModel saved =
        messages.save(newMessage(conversation, "Tem vaga para a manhã?", Instant.now()));

    String stored =
        jdbc.queryForObject(
            "select body from conversation_message where id = ?", String.class, saved.getId());

    assertThat(stored).startsWith("v1:").doesNotContain("vaga");
    assertThat(messages.findById(saved.getId()))
        .get()
        .extracting(message -> message.getBody())
        .isEqualTo("Tem vaga para a manhã?");
  }

  private ConversationModel newConversation(ClientModel client, DriverModel driver) {
    ConversationModel conversation = new ConversationModel();
    conversation.setClient(client);
    conversation.setDriver(driver);
    return conversation;
  }

  private ConversationMessageModel newMessage(
      ConversationModel conversation, String body, Instant sentAt) {
    ConversationMessageModel message = new ConversationMessageModel();
    message.setConversation(conversation);
    message.setSender(ConversationParticipant.CLIENT);
    message.setBody(body);
    message.setSentAt(sentAt);
    return message;
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private ClientModel createClient(String name, String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, name, email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String name, String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, name, email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }
}
