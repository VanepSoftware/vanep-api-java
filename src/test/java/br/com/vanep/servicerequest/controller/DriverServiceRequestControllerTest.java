package br.com.vanep.servicerequest.controller;

import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.model.UserModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverServiceRequestControllerTest {

  private static final String BASE = "/api/drivers/me/service-requests";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private ServiceRequestControllerFixtures fixtures;
  private ClientModel ana;
  private DriverModel carlos;
  private UserModel carlosUser;
  private DependentModel lucas;
  private String requestToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    fixtures = new ServiceRequestControllerFixtures(context);
    ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    carlosUser = carlos.getUser();
    lucas =
        fixtures.createDependentGoingEveryWeekday(
            ana, "Lucas Souza", LocalDate.now(TripService.SERVICE_ZONE).minusYears(10));
    requestToken = fixtures.sendRequest(ana, carlos, lucas);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/" + requestToken)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(post(BASE + "/" + requestToken + "/decline"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aClientCannotUseTheDriverSurface() throws Exception {
    UserModel client = ana.getUser();

    mockMvc
        .perform(get(BASE).with(as(client)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value(message("user.type.forbidden")));
    mockMvc
        .perform(get(BASE + "/" + requestToken).with(as(client)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(BASE + "/" + requestToken + "/decline").with(as(client)))
        .andExpect(status().isForbidden());
    assertThat(statusOf(requestToken)).isEqualTo("OPEN");
  }

  @Test
  void theDriverReadsOnlyWhatIsNeededToPrice() throws Exception {
    String body =
        mockMvc
            .perform(get(BASE + "/" + requestToken).with(as(carlosUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(requestToken))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.clientFirstName").value("Ana"))
            .andExpect(jsonPath("$.conversationToken").value(conversationTokenOf(ana, carlos)))
            .andExpect(jsonPath("$.notes").value("Portão azul"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.desiredStartDate").isNotEmpty())
            .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
            .andExpect(jsonPath("$.items[0].dependentFirstName").value("Lucas"))
            .andExpect(jsonPath("$.items[0].dependentAge").value(10))
            .andExpect(jsonPath("$.items[0].schoolName").value("Escola Municipal"))
            .andExpect(jsonPath("$.items[0].pickupCityName").value("Campinas"))
            .andExpect(jsonPath("$.items[0].pickupNeighborhood").value("Cambuí"))
            .andExpect(jsonPath("$.items[0].days.length()").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("Souza")
        .doesNotContain(lucas.getBirthDate().toString())
        .doesNotContain("Rua A")
        .doesNotContain("13015904")
        .doesNotContain("11144477735")
        .doesNotContain("ana@vanep.com")
        .doesNotContain("19999990000");
  }

  @Test
  void theDriverTalksToTheClientInTheConversationOfTheRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/conversations/" + conversationTokenOf(ana, carlos) + "/messages")
                .with(as(carlosUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Tenho vaga de manhã.\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sender").value("DRIVER"));
  }

  @Test
  void listsOnlyTheRequestsSentToTheCallerFilteredByStatus() throws Exception {
    ClientModel bruno = fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909");
    String fromBruno =
        fixtures.sendRequest(
            bruno,
            carlos,
            fixtures.createDependentGoingEveryWeekday(bruno, "Davi", LocalDate.of(2015, 3, 3)));
    DriverModel joana = fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705");
    fixtures.sendRequest(
        ana,
        joana,
        fixtures.createDependentGoingEveryWeekday(ana, "Marina", LocalDate.of(2018, 9, 1)));
    mockMvc
        .perform(post(BASE + "/" + fromBruno + "/decline").with(as(carlosUser)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(BASE).with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].token").value(fromBruno))
        .andExpect(jsonPath("$.content[0].items[0].dependentFirstName").value("Davi"))
        .andExpect(jsonPath("$.content[1].token").value(requestToken));
    mockMvc
        .perform(get(BASE).param("status", "DECLINED").with(as(carlosUser)))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(fromBruno));
    mockMvc
        .perform(get(BASE).param("status", "OPEN,DECLINED").with(as(carlosUser)))
        .andExpect(jsonPath("$.content.length()").value(2));
    mockMvc
        .perform(get(BASE).param("status", "SENT").with(as(carlosUser)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theConversationFilterListsOnlyTheRequestsOfThatPair() throws Exception {
    ClientModel bruno = fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909");
    fixtures.sendRequest(
        bruno,
        carlos,
        fixtures.createDependentGoingEveryWeekday(bruno, "Davi", LocalDate.of(2015, 3, 3)));
    DriverModel joana = fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705");
    fixtures.sendRequest(
        ana,
        joana,
        fixtures.createDependentGoingEveryWeekday(ana, "Marina", LocalDate.of(2018, 9, 1)));

    mockMvc
        .perform(
            get(BASE)
                .param("conversationToken", conversationTokenOf(ana, carlos))
                .with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].token").value(requestToken));
    mockMvc
        .perform(
            get(BASE)
                .param("conversationToken", conversationTokenOf(ana, joana))
                .with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void anotherDriversRequestIsNotFound() throws Exception {
    UserModel joana =
        fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705").getUser();

    mockMvc
        .perform(get(BASE + "/" + requestToken).with(as(joana)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_found")));
    mockMvc
        .perform(post(BASE + "/" + requestToken + "/decline").with(as(joana)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(BASE + "/unknown").with(as(carlosUser))).andExpect(status().isNotFound());
    assertThat(statusOf(requestToken)).isEqualTo("OPEN");
  }

  @Test
  void declinesWithAReasonOnce() throws Exception {
    mockMvc
        .perform(
            postJson(BASE + "/" + requestToken + "/decline", Map.of("reason", " Sem vaga "))
                .with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declineReason").value("Sem vaga"));
    mockMvc
        .perform(post(BASE + "/" + requestToken + "/decline").with(as(carlosUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_open")));
  }

  @Test
  void declinesWithoutABody() throws Exception {
    mockMvc
        .perform(post(BASE + "/" + requestToken + "/decline").with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declineReason").doesNotExist());
  }

  @Test
  void acceptsAReasonOfExactlyFiveHundredCharacters() throws Exception {
    mockMvc
        .perform(
            postJson(BASE + "/" + requestToken + "/decline", Map.of("reason", "a".repeat(500)))
                .with(as(carlosUser)))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsAnInvalidDeclineBodyWithoutChangingTheRequest() throws Exception {
    mockMvc
        .perform(
            postJson(BASE + "/" + requestToken + "/decline", Map.of("reason", "a".repeat(501)))
                .with(as(carlosUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            postJson(BASE + "/" + requestToken + "/decline", Map.of("reason", Map.of("text", "x")))
                .with(as(carlosUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post(BASE + "/" + requestToken + "/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": ")
                .with(as(carlosUser)))
        .andExpect(status().isBadRequest());
    assertThat(statusOf(requestToken)).isEqualTo("OPEN");
  }

  private String statusOf(String token) {
    return jdbc.queryForObject(
        "select status from service_request where token = ?", String.class, token);
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  private String conversationTokenOf(ClientModel client, DriverModel driver) {
    return jdbc.queryForObject(
        "select token from conversation where client_id = ? and driver_id = ?",
        String.class,
        client.getId(),
        driver.getId());
  }
}
