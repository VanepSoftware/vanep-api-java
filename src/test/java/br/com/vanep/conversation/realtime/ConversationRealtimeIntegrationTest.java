package br.com.vanep.conversation.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.conversation.dto.ConversationMessageCreateRequestDTO;
import br.com.vanep.conversation.model.ConversationModel;
import br.com.vanep.conversation.repository.ConversationMessageRepository;
import br.com.vanep.conversation.repository.ConversationRepository;
import br.com.vanep.conversation.service.ConversationService;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConversationRealtimeIntegrationTest {

  private static final long WAIT_SECONDS = 5;

  @LocalServerPort private int port;
  @Autowired private JWKSource<SecurityContext> jwkSource;
  @Autowired private ConversationRepository conversations;
  @Autowired private ConversationMessageRepository messages;
  @Autowired private ConversationService service;
  @Autowired private TransactionTemplate transactions;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private WebSocketStompClient stompClient;
  private ClientModel maria;
  private DriverModel carlos;
  private ConversationModel conversation;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    maria = createClient("Maria", "maria@vanep.com", "11144477735");
    carlos = createDriver("Carlos", "carlos@vanep.com", "52998224725");
    ConversationModel created = new ConversationModel();
    created.setClient(maria);
    created.setDriver(carlos);
    conversation = conversations.save(created);
  }

  @AfterEach
  void tearDown() {
    stompClient.stop();
  }

  @Test
  void theDriverReceivesTheClientMessageLive() throws Exception {
    BlockingQueue<Map<String, Object>> received = subscribe(connect(carlos.getUser()), topic());

    sendOverRest(maria.getUser(), "Tem vaga para a manhã?");

    Map<String, Object> payload = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(payload)
        .containsEntry("body", "Tem vaga para a manhã?")
        .containsEntry("sender", "CLIENT")
        .containsKey("token");
    assertThat(payload.get("sentAt")).isInstanceOf(String.class);
  }

  @Test
  void anOutsiderCannotSubscribe() throws Exception {
    UserModel stranger = createClient("Bruno", "bruno@vanep.com", "12345678909").getUser();
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    StompSession session = connect(stranger, errors);
    BlockingQueue<Map<String, Object>> received = subscribe(session, topic());

    assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    sendOverRest(maria.getUser(), "Oi");
    assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void aSubscriptionOutsideTheConversationTopicsIsRejected() throws Exception {
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    StompSession session = connect(carlos.getUser(), errors);

    subscribe(session, "/topic/anything");

    assertThat(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
  }

  @Test
  void theHandshakeRequiresABearerToken() {
    assertThatThrownBy(
            () ->
                stompClient
                    .connectAsync(url(), new StompSessionHandlerAdapter() {})
                    .get(WAIT_SECONDS, TimeUnit.SECONDS))
        .hasStackTraceContaining("401");
  }

  @Test
  void aMessageRolledBackIsNeverPublished() throws Exception {
    BlockingQueue<Map<String, Object>> received = subscribe(connect(carlos.getUser()), topic());

    transactions.executeWithoutResult(
        status -> {
          service.sendMessage(
              maria.getUser().getToken(),
              conversation.getToken(),
              new ConversationMessageCreateRequestDTO("Rascunho"));
          status.setRollbackOnly();
        });

    assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    assertThat(messages.count()).isZero();
  }

  private String topic() {
    return "/topic/conversations/" + conversation.getToken();
  }

  private String url() {
    return "ws://localhost:" + port + "/api/ws";
  }

  private StompSession connect(UserModel user) throws Exception {
    return connect(user, new LinkedBlockingQueue<>());
  }

  private StompSession connect(UserModel user, BlockingQueue<String> errors) throws Exception {
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(user));
    return stompClient
        .connectAsync(
            url(),
            handshakeHeaders,
            new StompSessionHandlerAdapter() {
              @Override
              public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message")));
              }

              @Override
              public void handleException(
                  StompSession session,
                  StompCommand command,
                  StompHeaders headers,
                  byte[] payload,
                  Throwable exception) {
                errors.add(exception.getMessage());
              }

              @Override
              public void handleTransportError(StompSession session, Throwable exception) {
                errors.add(String.valueOf(exception.getMessage()));
              }
            })
        .get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination)
      throws InterruptedException {
    BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
    StompSession.Receiptable subscription =
        session.subscribe(
            destination,
            new StompSessionHandlerAdapter() {
              @Override
              public Type getPayloadType(StompHeaders headers) {
                return Map.class;
              }

              @Override
              @SuppressWarnings("unchecked")
              public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
              }
            });
    assertThat(subscription).isNotNull();
    // The broker registers the subscription asynchronously after the SUBSCRIBE frame.
    Thread.sleep(300);
    return received;
  }

  private void sendOverRest(UserModel sender, String body) {
    RestClient.create("http://localhost:" + port)
        .post()
        .uri("/api/conversations/{token}/messages", conversation.getToken())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(sender))
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .body(Map.of("body", body))
        .retrieve()
        .toBodilessEntity();
  }

  private String tokenFor(UserModel user) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getToken())
            .claim("uid", user.getToken())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    return new NimbusJwtEncoder(jwkSource)
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
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
