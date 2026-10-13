package br.com.vanep.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.model.ConversationMessageModel;
import br.com.vanep.conversation.model.ConversationModel;
import br.com.vanep.conversation.repository.ConversationMessageRepository;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.driver.DriverApprovalStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConversationControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private ConversationRepository conversations;
  @Autowired private ConversationMessageRepository messages;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private MockMvc mockMvc;
  private ClientModel maria;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    maria = createClient("Maria", "maria@vanep.com", "11144477735");
    carlos = createDriver("Carlos", "carlos@vanep.com", "52998224725");
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/conversations/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aClientOpensANewConversation() throws Exception {
    mockMvc
        .perform(open(carlos.getToken()).with(clientJwt(maria)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.driverToken").value(carlos.getToken()))
        .andExpect(jsonPath("$.driverName").value("Carlos"))
        .andExpect(jsonPath("$.clientName").value("Maria"));
  }

  @Test
  void aClientReopensTheSameConversation() throws Exception {
    ConversationModel existing = conversations.save(newConversation(maria, carlos));

    mockMvc
        .perform(open(carlos.getToken()).with(clientJwt(maria)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(existing.getToken()));
    assertThat(conversations.count()).isEqualTo(1);
  }

  @Test
  void aDriverCannotOpenAConversation() throws Exception {
    mockMvc
        .perform(open(carlos.getToken()).with(driverJwt(carlos)))
        .andExpect(status().isForbidden());
  }

  @Test
  void aDriverThatIsNotSearchableCannotBeContacted() throws Exception {
    DriverModel underReview = createDriver("Helena", "helena@vanep.com", "15350946056");
    underReview.setApprovalStatus(DriverApprovalStatus.UNDER_REVIEW);
    drivers.save(underReview);

    mockMvc
        .perform(open(underReview.getToken()).with(clientJwt(maria)))
        .andExpect(status().isNotFound());
    mockMvc.perform(open("unknown").with(clientJwt(maria))).andExpect(status().isNotFound());
  }

  @Test
  void openingRequiresTheDriverToken() throws Exception {
    mockMvc
        .perform(
            post("/api/conversations")
                .with(clientJwt(maria))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverToken\":\" \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theDriverSeesOnlyTheirConversationsMostRecentFirst() throws Exception {
    ClientModel bruno = createClient("Bruno", "bruno@vanep.com", "12345678909");
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ConversationModel withMaria = newConversation(maria, carlos);
    withMaria.setLastMessageAt(Instant.parse("2020-01-01T09:00:00Z"));
    ConversationModel withBruno = newConversation(bruno, carlos);
    withBruno.setLastMessageAt(Instant.parse("2020-01-01T10:00:00Z"));
    conversations.saveAll(List.of(withMaria, withBruno, newConversation(maria, helena)));

    mockMvc
        .perform(get("/api/conversations/me").with(driverJwt(carlos)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].clientName").value("Bruno"))
        .andExpect(jsonPath("$[1].clientName").value("Maria"));
  }

  @Test
  void theClientSeesOnlyTheirConversationsMostRecentFirst() throws Exception {
    DriverModel helena = createDriver("Helena", "helena@vanep.com", "15350946056");
    ClientModel bruno = createClient("Bruno", "bruno@vanep.com", "12345678909");
    ConversationModel withCarlos = newConversation(maria, carlos);
    withCarlos.setLastMessageAt(Instant.parse("2020-01-01T09:00:00Z"));
    ConversationModel withHelena = newConversation(maria, helena);
    withHelena.setLastMessageAt(Instant.parse("2020-01-01T10:00:00Z"));
    conversations.saveAll(List.of(withCarlos, withHelena, newConversation(bruno, carlos)));

    mockMvc
        .perform(get("/api/conversations/me").with(clientJwt(maria)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].driverName").value("Helena"))
        .andExpect(jsonPath("$[1].driverName").value("Carlos"));
  }

  @Test
  void listingRequiresAConversationListPermission() throws Exception {
    mockMvc
        .perform(get("/api/conversations/me").with(partyJwt(maria.getUser())))
        .andExpect(status().isForbidden());
  }

  @Test
  void bothPartiesSendMessages() throws Exception {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));

    mockMvc
        .perform(send(conversation, "Tem vaga para a manhã?").with(partyJwt(maria.getUser())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.body").value("Tem vaga para a manhã?"))
        .andExpect(jsonPath("$.sender").value("CLIENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
    mockMvc
        .perform(send(conversation, "Tenho sim.").with(partyJwt(carlos.getUser())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sender").value("DRIVER"));

    assertThat(conversations.findByToken(conversation.getToken()).orElseThrow().getLastMessageAt())
        .isNotNull();
  }

  @Test
  void anOutsiderCannotReadNorSend() throws Exception {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));
    UserModel stranger = createClient("Bruno", "bruno@vanep.com", "12345678909").getUser();

    mockMvc
        .perform(history(conversation).with(partyJwt(stranger)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(send(conversation, "Oi").with(partyJwt(stranger)))
        .andExpect(status().isForbidden());
    assertThat(messages.count()).isZero();
  }

  @Test
  void blankAndTooLongMessagesAreRejected() throws Exception {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));

    mockMvc
        .perform(send(conversation, "   ").with(partyJwt(maria.getUser())))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(send(conversation, "a".repeat(2001)).with(partyJwt(maria.getUser())))
        .andExpect(status().isBadRequest());
    assertThat(messages.count()).isZero();
  }

  @Test
  void theHistoryIsPaginatedNewestFirst() throws Exception {
    ConversationModel conversation = conversations.save(newConversation(maria, carlos));
    Instant start = Instant.parse("2020-01-01T09:00:00Z");
    for (int minute = 0; minute < 45; minute++) {
      messages.save(newMessage(conversation, "message " + minute, start.plusSeconds(60L * minute)));
    }

    mockMvc
        .perform(history(conversation).param("page", "0").with(partyJwt(carlos.getUser())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(30))
        .andExpect(jsonPath("$.content[0].body").value("message 44"))
        .andExpect(jsonPath("$.content[29].body").value("message 15"))
        .andExpect(jsonPath("$.totalElements").value(45));
  }

  private MockHttpServletRequestBuilder open(String driverToken) {
    return post("/api/conversations")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"driverToken\":\"" + driverToken + "\"}");
  }

  private MockHttpServletRequestBuilder send(ConversationModel conversation, String body) {
    return post("/api/conversations/" + conversation.getToken() + "/messages")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"body\":\"" + body + "\"}");
  }

  private MockHttpServletRequestBuilder history(ConversationModel conversation) {
    return get("/api/conversations/" + conversation.getToken() + "/messages");
  }

  private JwtRequestPostProcessor clientJwt(ClientModel client) {
    return partyJwt(client.getUser())
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("create_conversation"),
            new SimpleGrantedAuthority("list_client_conversations"));
  }

  private JwtRequestPostProcessor driverJwt(DriverModel driver) {
    return partyJwt(driver.getUser())
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("list_driver_conversations"));
  }

  private JwtRequestPostProcessor partyJwt(UserModel user) {
    return jwt().jwt(token -> token.claim("uid", user.getToken()).subject(user.getToken()));
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
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
  }
}
