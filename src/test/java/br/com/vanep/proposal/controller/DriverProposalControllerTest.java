package br.com.vanep.proposal.controller;

import static br.com.vanep.proposal.controller.ProposalControllerFixtures.proposalBody;
import static br.com.vanep.proposal.controller.ProposalControllerFixtures.proposalItemBody;
import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.vehicle.model.VehicleModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverProposalControllerTest {

  private static final String REQUESTS = "/api/drivers/me/service-requests";
  private static final String PROPOSALS = "/api/drivers/me/proposals";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private ServiceRequestControllerFixtures fixtures;
  private ProposalControllerFixtures proposalFixtures;
  private ClientModel ana;
  private DriverModel carlos;
  private UserModel carlosUser;
  private DependentModel lucas;
  private VehicleModel van;
  private String requestToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    fixtures = new ServiceRequestControllerFixtures(context);
    proposalFixtures = new ProposalControllerFixtures(context);
    ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    carlosUser = carlos.getUser();
    lucas = fixtures.createDependentGoingEveryWeekday(ana, "Lucas Souza", LocalDate.of(2016, 5, 4));
    van = proposalFixtures.createVehicle(carlos, "VAN1A23");
    requestToken = fixtures.sendRequest(ana, carlos, lucas);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(postJson(chainOf(requestToken), validBody()))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get(chainOf(requestToken))).andExpect(status().isUnauthorized());
    mockMvc.perform(get(PROPOSALS + "/unknown")).andExpect(status().isUnauthorized());
    mockMvc.perform(post(PROPOSALS + "/unknown/withdraw")).andExpect(status().isUnauthorized());
  }

  @Test
  void aClientCannotUseTheDriverSurface() throws Exception {
    String proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);
    UserModel client = ana.getUser();

    mockMvc
        .perform(postJson(chainOf(requestToken), validBody()).with(as(client)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value(message("user.type.forbidden")));
    mockMvc.perform(get(chainOf(requestToken)).with(as(client))).andExpect(status().isForbidden());
    mockMvc
        .perform(get(PROPOSALS + "/" + proposalToken).with(as(client)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(PROPOSALS + "/" + proposalToken + "/withdraw").with(as(client)))
        .andExpect(status().isForbidden());
    assertThat(proposalStatuses()).containsExactly("SENT");
  }

  @Test
  void sendsTheFirstVersionAndAnswersTheRequest() throws Exception {
    String body =
        mockMvc
            .perform(postJson(chainOf(requestToken), validBody()).with(as(carlosUser)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.serviceRequestToken").value(requestToken))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.validUntil").isNotEmpty())
            .andExpect(jsonPath("$.totalAmount").value(4200.00))
            .andExpect(jsonPath("$.vehicle.plate").value("VAN1A23"))
            .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
            .andExpect(jsonPath("$.items[0].dependentName").doesNotExist())
            .andExpect(jsonPath("$.items[0].dependentFirstName").value("Lucas"))
            .andExpect(jsonPath("$.items[0].slots.length()").value(10))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("Souza");
    assertThat(requestStatus()).isEqualTo("ANSWERED");
  }

  @Test
  void sendsANewVersionThatSupersedesTheCurrentOne() throws Exception {
    proposalFixtures.sendProposal(carlos, requestToken, van, lucas);

    mockMvc
        .perform(postJson(chainOf(requestToken), validBody()).with(as(carlosUser)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2));
    mockMvc
        .perform(get(chainOf(requestToken)).with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].version").value(1))
        .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
        .andExpect(jsonPath("$[1].version").value(2))
        .andExpect(jsonPath("$[1].status").value("SENT"));
  }

  @Test
  void rejectsAnInvalidBodyWithoutSendingAProposal() throws Exception {
    for (Map<String, Object> invalid :
        List.of(
            validBodyWith("totalAmount", "0"),
            validBodyWith("totalAmount", "10.001"),
            validBodyWith("installments", 13),
            validBodyWith("installments", "dez"),
            validBodyWith("dueDay", 29),
            validBodyWith("validDays", 8),
            validBodyWith("startsOn", LocalDate.now().minusDays(1).toString()),
            validBodyWith("adjustmentPolicy", "SOMETIMES"),
            validBodyWith("vehicleToken", " "),
            validBodyWith("message", "a".repeat(1001)),
            validBodyWith("items", List.of()))) {
      mockMvc
          .perform(postJson(chainOf(requestToken), invalid).with(as(carlosUser)))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            post(chainOf(requestToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"totalAmount\": ")
                .with(as(carlosUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(post(chainOf(requestToken)).with(as(carlosUser)))
        .andExpect(status().isBadRequest());

    assertThat(proposalStatuses()).isEmpty();
    assertThat(requestStatus()).isEqualTo("OPEN");
  }

  @Test
  void acceptsTheLimitsOfTheBody() throws Exception {
    Map<String, Object> body = validBodyWith("installments", 12);
    body.put("dueDay", 28);
    body.put("validDays", 7);
    body.put("message", "a".repeat(1000));

    mockMvc
        .perform(postJson(chainOf(requestToken), body).with(as(carlosUser)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.installments").value(12))
        .andExpect(jsonPath("$.dueDay").value(28));
  }

  @Test
  void refusesItemsForAnotherSetOfDependents() throws Exception {
    DependentModel marina =
        fixtures.createDependentGoingEveryWeekday(ana, "Marina Souza", LocalDate.of(2018, 9, 1));

    mockMvc
        .perform(
            postJson(chainOf(requestToken), bodyFor(van.getToken(), lucas, marina))
                .with(as(carlosUser)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("proposal.items.mismatch")));
    assertThat(proposalStatuses()).isEmpty();
  }

  @Test
  void refusesTheVehicleOfAnotherDriver() throws Exception {
    DriverModel joana = fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705");
    VehicleModel joanasVan = proposalFixtures.createVehicle(joana, "JOA2B34");

    mockMvc
        .perform(
            postJson(chainOf(requestToken), bodyFor(joanasVan.getToken(), lucas))
                .with(as(carlosUser)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("proposal.vehicle.not_found")));
    assertThat(proposalStatuses()).isEmpty();
  }

  @Test
  void refusesAProposalForADeclinedRequest() throws Exception {
    mockMvc
        .perform(post(REQUESTS + "/" + requestToken + "/decline").with(as(carlosUser)))
        .andExpect(status().isOk());

    mockMvc
        .perform(postJson(chainOf(requestToken), validBody()).with(as(carlosUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("proposal.request.not_open")));
    assertThat(proposalStatuses()).isEmpty();
  }

  @Test
  void anotherDriversRequestAndProposalAreNotFound() throws Exception {
    String proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);
    DriverModel joana = fixtures.createAvailableDriver("Joana", "joana@vanep.com", "39053344705");
    UserModel joanaUser = joana.getUser();

    mockMvc
        .perform(
            postJson(
                    chainOf(requestToken),
                    bodyFor(proposalFixtures.createVehicle(joana, "JOA2B34").getToken(), lucas))
                .with(as(joanaUser)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_found")));
    mockMvc
        .perform(get(chainOf(requestToken)).with(as(joanaUser)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(PROPOSALS + "/" + proposalToken).with(as(joanaUser)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_found")));
    mockMvc
        .perform(post(PROPOSALS + "/" + proposalToken + "/withdraw").with(as(joanaUser)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get(PROPOSALS + "/unknown").with(as(carlosUser)))
        .andExpect(status().isNotFound());
    assertThat(proposalStatuses()).containsExactly("SENT");
  }

  @Test
  void readsTheProposalWithTheDependentByFirstName() throws Exception {
    String proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);

    mockMvc
        .perform(get(PROPOSALS + "/" + proposalToken).with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(proposalToken))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.message").value("Saio às 6h20"))
        .andExpect(jsonPath("$.items[0].dependentName").doesNotExist())
        .andExpect(jsonPath("$.items[0].dependentFirstName").value("Lucas"))
        .andExpect(jsonPath("$.items[0].slots[0].weekday").value("MONDAY"))
        .andExpect(jsonPath("$.items[0].slots[0].leg").value("OUTBOUND"));
  }

  @Test
  void withdrawsTheCurrentVersionOnce() throws Exception {
    String proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);

    mockMvc
        .perform(post(PROPOSALS + "/" + proposalToken + "/withdraw").with(as(carlosUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    assertThat(requestStatus()).isEqualTo("OPEN");
    mockMvc
        .perform(post(PROPOSALS + "/" + proposalToken + "/withdraw").with(as(carlosUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_current")));
    mockMvc
        .perform(postJson(chainOf(requestToken), validBody()).with(as(carlosUser)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void aProposalCannotBeEdited() throws Exception {
    String proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);
    Map<String, Object> body = validBody();

    mockMvc
        .perform(withJson(patch(PROPOSALS + "/" + proposalToken), body).with(as(carlosUser)))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(withJson(put(PROPOSALS + "/" + proposalToken), body).with(as(carlosUser)))
        .andExpect(status().isMethodNotAllowed());
    assertThat(proposalStatuses()).containsExactly("SENT");
  }

  private String chainOf(String token) {
    return REQUESTS + "/" + token + "/proposals";
  }

  private Map<String, Object> validBody() {
    return bodyFor(van.getToken(), lucas);
  }

  private Map<String, Object> validBodyWith(String field, Object value) {
    Map<String, Object> body = validBody();
    body.put(field, value);
    return body;
  }

  private Map<String, Object> bodyFor(String vehicleToken, DependentModel... offered) {
    return proposalBody(
        vehicleToken,
        List.of(offered).stream()
            .map(dependent -> proposalItemBody(dependent.getToken()))
            .toList());
  }

  private List<String> proposalStatuses() {
    return jdbc.queryForList("select status from proposal order by version", String.class);
  }

  private String requestStatus() {
    return jdbc.queryForObject(
        "select status from service_request where token = ?", String.class, requestToken);
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
    return withJson(post(path), body);
  }

  private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body)
      throws Exception {
    return builder
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
