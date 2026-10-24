package br.com.vanep.servicerequest.controller;

import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.as;
import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.createBody;
import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.dayBody;
import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.itemBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.model.UserModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientServiceRequestControllerTest {

  private static final String BASE = "/api/clients/me/service-requests";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private ServiceRequestControllerFixtures fixtures;
  private ClientModel ana;
  private UserModel anaUser;
  private DriverModel carlos;
  private DependentModel lucas;
  private DependentModel marina;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    fixtures = new ServiceRequestControllerFixtures(context);
    ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    anaUser = ana.getUser();
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    lucas = fixtures.createDependentGoingEveryWeekday(ana, "Lucas Souza", LocalDate.of(2016, 4, 2));
    marina =
        fixtures.createDependentGoingEveryWeekday(ana, "Marina Souza", LocalDate.of(2018, 9, 1));
  }

  @Test
  void requiresAuthentication() throws Exception {
    String token = fixtures.sendRequest(ana, carlos, lucas);

    mockMvc.perform(postJson(BASE, validBody())).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(BASE + "/" + token)).andExpect(status().isUnauthorized());
    mockMvc.perform(post(BASE + "/" + token + "/withdraw")).andExpect(status().isUnauthorized());
  }

  @Test
  void aDriverCannotSendOrReadAsAClient() throws Exception {
    String token = fixtures.sendRequest(ana, carlos, lucas);
    UserModel driver = carlos.getUser();

    mockMvc
        .perform(postJson(BASE, validBody()).with(as(driver)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value(message("user.type.forbidden")));
    mockMvc.perform(get(BASE).with(as(driver))).andExpect(status().isForbidden());
    mockMvc.perform(get(BASE + "/" + token).with(as(driver))).andExpect(status().isForbidden());
    mockMvc
        .perform(post(BASE + "/" + token + "/withdraw").with(as(driver)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdminCannotSendAsAClient() throws Exception {
    UserModel admin = fixtures.createAdmin("admin@vanep.com", "39053344705");

    mockMvc.perform(postJson(BASE, validBody()).with(as(admin))).andExpect(status().isForbidden());
    assertThat(countRequests()).isZero();
  }

  @Test
  void aReadyClientAsksForAQuoteForTwoSiblings() throws Exception {
    Instant before = Instant.now();

    String body =
        mockMvc
            .perform(
                postJson(
                        BASE,
                        createBody(
                            carlos.getToken(),
                            List.of(itemBody(lucas.getToken()), itemBody(marina.getToken()))))
                    .with(as(anaUser)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.driverToken").value(carlos.getToken()))
            .andExpect(jsonPath("$.driverName").value("Carlos Lima"))
            .andExpect(jsonPath("$.clientDriverToken").isNotEmpty())
            .andExpect(jsonPath("$.notes").value("Portão azul"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
            .andExpect(jsonPath("$.items[0].dependentName").value("Lucas Souza"))
            .andExpect(jsonPath("$.items[0].dependentBirthDate").value("2016-04-02"))
            .andExpect(jsonPath("$.items[0].schoolName").value("Escola Municipal"))
            .andExpect(jsonPath("$.items[0].pickupCityName").value("Campinas"))
            .andExpect(jsonPath("$.items[0].pickupStateUf").value("SP"))
            .andExpect(jsonPath("$.items[0].pickupNeighborhood").value("Cambuí"))
            .andExpect(jsonPath("$.items[0].days.length()").value(5))
            .andExpect(jsonPath("$.items[0].days[0].weekday").value("MONDAY"))
            .andExpect(jsonPath("$.items[0].days[0].schoolShift").value("MORNING"))
            .andExpect(jsonPath("$.items[1].days.length()").value(5))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Instant expiresAt = Instant.parse(objectMapper.readTree(body).get("expiresAt").asText());
    assertThat(expiresAt)
        .isBetween(before.plus(Duration.ofHours(72)), Instant.now().plus(Duration.ofHours(72)));
    assertThat(jdbc.queryForObject("select count(*) from client_driver", Long.class)).isOne();
  }

  @Test
  void eachDayTakesThePeriodOfTheGridAndTheLegsTheClientSent() throws Exception {
    DependentModel teo =
        fixtures.createDependent(
            ana,
            "Teo Souza",
            LocalDate.of(2017, 1, 10),
            weekday ->
                weekday == DayOfWeek.TUESDAY || weekday == DayOfWeek.THURSDAY
                    ? SchoolShift.FULLTIME
                    : SchoolShift.MORNING);
    Map<String, Object> item =
        Map.of(
            "dependentToken",
            teo.getToken(),
            "days",
            List.of(
                dayBody("TUESDAY", true, true),
                dayBody("WEDNESDAY", true, false),
                dayBody("THURSDAY", true, true)));

    mockMvc
        .perform(postJson(BASE, createBody(carlos.getToken(), List.of(item))).with(as(anaUser)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].days[0].weekday").value("TUESDAY"))
        .andExpect(jsonPath("$.items[0].days[0].schoolShift").value("FULLTIME"))
        .andExpect(jsonPath("$.items[0].days[1].schoolShift").value("MORNING"))
        .andExpect(jsonPath("$.items[0].days[1].needsOutbound").value(true))
        .andExpect(jsonPath("$.items[0].days[1].needsReturn").value(false))
        .andExpect(jsonPath("$.items[0].days[2].schoolShift").value("FULLTIME"));
  }

  @Test
  void acceptsNotesOfExactlyOneThousandCharacters() throws Exception {
    mockMvc
        .perform(postJson(BASE, bodyWith("notes", "a".repeat(1000))).with(as(anaUser)))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsAnInvalidBodyWithoutCreatingAnything() throws Exception {
    expectBadRequest(bodyWith("driverToken", null));
    expectBadRequest(bodyWith("driverToken", " "));
    expectBadRequest(bodyWith("desiredStartDate", null));
    expectFieldError(
        bodyWith("desiredStartDate", LocalDate.now().minusDays(1).toString()),
        "desiredStartDate",
        "service_request.desired_start_date.past");
    expectBadRequest(bodyWith("notes", "a".repeat(1001)));
    expectFieldError(bodyWith("items", List.of()), "items", "service_request.items.required");
    expectFieldError(
        bodyWith("items", List.of(Map.of("dependentToken", lucas.getToken(), "days", List.of()))),
        "items[0].days",
        "service_request.days.required");
    expectBadRequestDetail(
        bodyWith("items", List.of(itemBody(lucas.getToken()), itemBody(lucas.getToken()))),
        "service_request.item.dependent_duplicate");
    expectBadRequestDetail(
        bodyWith(
            "items",
            List.of(
                Map.of(
                    "dependentToken",
                    lucas.getToken(),
                    "days",
                    List.of(dayBody("MONDAY", true, true), dayBody("MONDAY", true, false))))),
        "service_request.day.weekday_duplicate");
    expectBadRequestDetail(
        bodyWith(
            "items",
            List.of(
                Map.of(
                    "dependentToken",
                    lucas.getToken(),
                    "days",
                    List.of(dayBody("MONDAY", false, false))))),
        "service_request.day.leg_required");
    expectBadRequest(
        bodyWith(
            "items",
            List.of(
                Map.of(
                    "dependentToken",
                    lucas.getToken(),
                    "days",
                    List.of(Map.of("weekday", "MONDAY", "needsOutbound", true))))));
    expectBadRequest(
        bodyWith(
            "items",
            List.of(
                Map.of(
                    "dependentToken",
                    lucas.getToken(),
                    "days",
                    List.of(
                        Map.of(
                            "weekday", "SOMEDAY", "needsOutbound", true, "needsReturn", true))))));
    expectBadRequest(bodyWith("items", "Lucas"));
    expectBadRequest(bodyWith("desiredStartDate", "amanhã"));

    mockMvc
        .perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"driverToken\": ")
                .with(as(anaUser)))
        .andExpect(status().isBadRequest());

    assertThat(countRequests()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from client_driver", Long.class)).isZero();
  }

  @Test
  void aDayWithoutSchoolIsRefused() throws Exception {
    Map<String, Object> item =
        Map.of(
            "dependentToken", lucas.getToken(), "days", List.of(dayBody("SATURDAY", true, true)));

    mockMvc
        .perform(postJson(BASE, createBody(carlos.getToken(), List.of(item))).with(as(anaUser)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("service_request.day.no_school")));
    assertThat(countRequests()).isZero();
  }

  @Test
  void anotherClientsDependentIsNotFound() throws Exception {
    ClientModel bruno = fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909");
    DependentModel davi =
        fixtures.createDependentGoingEveryWeekday(bruno, "Davi", LocalDate.of(2015, 3, 3));

    mockMvc
        .perform(
            postJson(BASE, createBody(carlos.getToken(), List.of(itemBody(davi.getToken()))))
                .with(as(anaUser)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.dependent.not_found")));
  }

  @Test
  void anUnknownDriverIsNotFound() throws Exception {
    mockMvc
        .perform(postJson(BASE, bodyWith("driverToken", "unknown")).with(as(anaUser)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.driver.not_found")));
  }

  @Test
  void aSecondOpenRequestToTheSameDriverConflicts() throws Exception {
    fixtures.sendRequest(ana, carlos, lucas);

    mockMvc
        .perform(postJson(BASE, validBody()).with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("service_request.open_exists")));
    assertThat(countRequests()).isOne();
  }

  @Test
  void listsOnlyTheCallersRequestsFilteredByStatus() throws Exception {
    DriverModel joana = fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705");
    String toCarlos = fixtures.sendRequest(ana, carlos, lucas);
    String toJoana = fixtures.sendRequest(ana, joana, marina);
    mockMvc
        .perform(post(BASE + "/" + toJoana + "/withdraw").with(as(anaUser)))
        .andExpect(status().isOk());
    ClientModel bruno = fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909");
    fixtures.sendRequest(
        bruno,
        carlos,
        fixtures.createDependentGoingEveryWeekday(bruno, "Davi", LocalDate.of(2015, 3, 3)));

    mockMvc
        .perform(get(BASE).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].token").value(toJoana))
        .andExpect(jsonPath("$.content[0].items[0].dependentName").value("Marina Souza"))
        .andExpect(jsonPath("$.content[1].token").value(toCarlos));
    mockMvc
        .perform(get(BASE).param("status", "OPEN").with(as(anaUser)))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(toCarlos));
    mockMvc
        .perform(get(BASE).param("status", "OPEN,WITHDRAWN").with(as(anaUser)))
        .andExpect(jsonPath("$.content.length()").value(2));
    mockMvc
        .perform(get(BASE).param("status", "WITHDRAWN", "DECLINED").with(as(anaUser)))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(toJoana));
    mockMvc
        .perform(get(BASE).param("size", "1").with(as(anaUser)))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2));
    mockMvc
        .perform(get(BASE).param("status", "PENDING").with(as(anaUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get(BASE).param("status", "OPEN,PENDING").with(as(anaUser)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void readsOwnRequest() throws Exception {
    String token = fixtures.sendRequest(ana, carlos, lucas);

    mockMvc
        .perform(get(BASE + "/" + token).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(token))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.items[0].dependentName").value("Lucas Souza"));
  }

  @Test
  void anotherClientsRequestIsNotFound() throws Exception {
    String token = fixtures.sendRequest(ana, carlos, lucas);
    UserModel bruno =
        fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909").getUser();

    mockMvc
        .perform(get(BASE + "/" + token).with(as(bruno)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_found")));
    mockMvc
        .perform(post(BASE + "/" + token + "/withdraw").with(as(bruno)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(BASE + "/unknown").with(as(anaUser))).andExpect(status().isNotFound());
    assertThat(statusOf(token)).isEqualTo("OPEN");
  }

  @Test
  void withdrawsAnOpenRequestOnce() throws Exception {
    String token = fixtures.sendRequest(ana, carlos, lucas);

    mockMvc
        .perform(post(BASE + "/" + token + "/withdraw").with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(token))
        .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    mockMvc
        .perform(post(BASE + "/" + token + "/withdraw").with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_open")));
  }

  private void expectBadRequest(Map<String, Object> body) throws Exception {
    mockMvc.perform(postJson(BASE, body).with(as(anaUser))).andExpect(status().isBadRequest());
  }

  private void expectFieldError(Map<String, Object> body, String field, String key)
      throws Exception {
    mockMvc
        .perform(postJson(BASE, body).with(as(anaUser)))
        .andExpect(status().isBadRequest())
        .andExpect(
            result -> {
              MethodArgumentNotValidException exception =
                  (MethodArgumentNotValidException) result.getResolvedException();
              assertThat(exception.getBindingResult().getFieldError(field).getDefaultMessage())
                  .isEqualTo(message(key));
            });
  }

  private void expectBadRequestDetail(Map<String, Object> body, String key) throws Exception {
    mockMvc
        .perform(postJson(BASE, body).with(as(anaUser)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message(key)));
  }

  private Map<String, Object> validBody() {
    return createBody(carlos.getToken(), List.of(itemBody(lucas.getToken())));
  }

  private Map<String, Object> bodyWith(String field, Object value) {
    Map<String, Object> body = new HashMap<>(validBody());
    body.put(field, value);
    return body;
  }

  private long countRequests() {
    return jdbc.queryForObject("select count(*) from service_request", Long.class);
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
}
