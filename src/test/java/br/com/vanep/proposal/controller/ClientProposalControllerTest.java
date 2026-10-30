package br.com.vanep.proposal.controller;

import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientProposalControllerTest {

  private static final String REQUESTS = "/api/clients/me/service-requests";
  private static final String PROPOSALS = "/api/clients/me/proposals";
  private static final String DRIVER_REQUESTS = "/api/drivers/me/service-requests";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private ServiceRequestControllerFixtures fixtures;
  private ProposalControllerFixtures proposalFixtures;
  private UserModel anaUser;
  private DriverModel carlos;
  private DependentModel lucas;
  private VehicleModel van;
  private String requestToken;
  private String proposalToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    fixtures = new ServiceRequestControllerFixtures(context);
    proposalFixtures = new ProposalControllerFixtures(context);
    ClientModel ana = fixtures.createReadyClient("Ana Souza", "ana@vanep.com", "11144477735");
    anaUser = ana.getUser();
    carlos = fixtures.createAvailableDriver("Carlos Lima", "carlos@vanep.com", "52998224725");
    lucas = fixtures.createDependentGoingEveryWeekday(ana, "Lucas Souza", LocalDate.of(2016, 5, 4));
    van = proposalFixtures.createVehicle(carlos, "VAN1A23");
    requestToken = fixtures.sendRequest(ana, carlos, lucas);
    proposalToken = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get(chainOf(requestToken))).andExpect(status().isUnauthorized());
    mockMvc.perform(get(proposal())).andExpect(status().isUnauthorized());
    mockMvc.perform(post(proposal() + "/accept")).andExpect(status().isUnauthorized());
    mockMvc.perform(post(proposal() + "/decline")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()))
        .andExpect(status().isUnauthorized());
    assertThat(proposalStatuses()).containsExactly("SENT");
  }

  @Test
  void aDriverCannotUseTheClientSurface() throws Exception {
    UserModel driver = carlos.getUser();

    mockMvc
        .perform(get(chainOf(requestToken)).with(as(driver)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value(message("user.type.forbidden")));
    mockMvc.perform(get(proposal()).with(as(driver))).andExpect(status().isForbidden());
    mockMvc
        .perform(post(proposal() + "/accept").with(as(driver)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(proposal() + "/decline").with(as(driver)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()).with(as(driver)))
        .andExpect(status().isForbidden());
    assertThat(proposalStatuses()).containsExactly("SENT");
    assertThat(adjustmentCount()).isZero();
  }

  @Test
  void anotherClientsProposalIsNotFound() throws Exception {
    UserModel bruno =
        fixtures.createReadyClient("Bruno", "bruno@vanep.com", "12345678909").getUser();

    mockMvc
        .perform(get(chainOf(requestToken)).with(as(bruno)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("service_request.not_found")));
    mockMvc
        .perform(get(proposal()).with(as(bruno)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_found")));
    mockMvc.perform(post(proposal() + "/accept").with(as(bruno))).andExpect(status().isNotFound());
    mockMvc.perform(post(proposal() + "/decline").with(as(bruno))).andExpect(status().isNotFound());
    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()).with(as(bruno)))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(PROPOSALS + "/unknown").with(as(anaUser))).andExpect(status().isNotFound());
    assertThat(proposalStatuses()).containsExactly("SENT");
    assertThat(adjustmentCount()).isZero();
  }

  @Test
  void readsTheProposalWithTheDependentsFullName() throws Exception {
    mockMvc
        .perform(get(proposal()).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(proposalToken))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.vehicle.plate").value("VAN1A23"))
        .andExpect(jsonPath("$.totalAmount").value(4200.0))
        .andExpect(jsonPath("$.installments").value(10))
        .andExpect(jsonPath("$.installmentAmount").value(420.0))
        .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
        .andExpect(jsonPath("$.items[0].dependentName").value("Lucas Souza"))
        .andExpect(jsonPath("$.items[0].dependentFirstName").value("Lucas"));
  }

  @Test
  void listsTheChainOldestFirstAndRefusesToAcceptASupersededVersion() throws Exception {
    String second = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);

    mockMvc
        .perform(get(chainOf(requestToken)).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].token").value(proposalToken))
        .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
        .andExpect(jsonPath("$[1].token").value(second))
        .andExpect(jsonPath("$[1].status").value("SENT"));
    mockMvc
        .perform(post(proposal() + "/accept").with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_current")));
    assertThat(proposalStatuses()).containsExactly("SUPERSEDED", "SENT");
  }

  @Test
  void bothRequestListsFollowTheCurrentProposalThroughAdjustmentNewVersionAndAccept()
      throws Exception {
    UserModel driver = carlos.getUser();
    mockMvc
        .perform(get(REQUESTS).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("ANSWERED"))
        .andExpect(jsonPath("$.content[0].currentProposal.token").value(proposalToken))
        .andExpect(jsonPath("$.content[0].currentProposal.version").value(1))
        .andExpect(jsonPath("$.content[0].currentProposal.status").value("SENT"))
        .andExpect(jsonPath("$.content[0].currentProposal.installmentAmount").value(420.0))
        .andExpect(jsonPath("$.content[0].currentProposal.validUntil").isNotEmpty())
        .andExpect(
            jsonPath("$.content[0].currentProposal.startsOn")
                .value(ProposalControllerFixtures.startsOn().toString()))
        .andExpect(jsonPath("$.content[0].currentProposal.hasPendingAdjustment").value(false));

    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()).with(as(anaUser)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get(REQUESTS).with(as(anaUser)))
        .andExpect(jsonPath("$.content[0].currentProposal.hasPendingAdjustment").value(true));
    mockMvc
        .perform(get(DRIVER_REQUESTS).with(as(driver)))
        .andExpect(jsonPath("$.content[0].currentProposal.token").value(proposalToken))
        .andExpect(jsonPath("$.content[0].currentProposal.hasPendingAdjustment").value(true));

    String second = proposalFixtures.sendProposal(carlos, requestToken, van, lucas);
    mockMvc
        .perform(get(DRIVER_REQUESTS + "/" + requestToken).with(as(driver)))
        .andExpect(jsonPath("$.currentProposal.token").value(second))
        .andExpect(jsonPath("$.currentProposal.version").value(2))
        .andExpect(jsonPath("$.currentProposal.hasPendingAdjustment").value(false));

    mockMvc
        .perform(post(PROPOSALS + "/" + second + "/accept").with(as(anaUser)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(REQUESTS).param("status", "CONVERTED").with(as(anaUser)))
        .andExpect(jsonPath("$.content[0].currentProposal.token").value(second))
        .andExpect(jsonPath("$.content[0].currentProposal.status").value("ACCEPTED"));
  }

  @Test
  void aWithdrawnProposalLeavesTheReopenedRequestWithoutACurrentOne() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/proposals/" + proposalToken + "/withdraw")
                .with(as(carlos.getUser())))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(REQUESTS + "/" + requestToken).with(as(anaUser)))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.currentProposal").value(nullValue()));
  }

  @Test
  void acceptsTheCurrentVersionWithoutCreatingAContract() throws Exception {
    String sent =
        mockMvc
            .perform(get(proposal()).with(as(anaUser)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(post(proposal() + "/accept").with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.acceptedAt").isNotEmpty());

    assertThat(requestStatus()).isEqualTo("CONVERTED");
    assertThat(jdbc.queryForObject("select count(*) from contract", Integer.class)).isZero();
    String accepted =
        mockMvc
            .perform(get(proposal()).with(as(anaUser)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(termsOf(accepted)).isEqualTo(termsOf(sent));
    mockMvc
        .perform(post(proposal() + "/accept").with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_current")));
  }

  @Test
  void declinesWithAReason() throws Exception {
    mockMvc
        .perform(
            postJson(proposal() + "/decline", Map.of("reason", " Muito caro ")).with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declineReason").value("Muito caro"));
    assertThat(requestStatus()).isEqualTo("WITHDRAWN");
  }

  @Test
  void declinesWithoutABody() throws Exception {
    mockMvc
        .perform(post(proposal() + "/decline").with(as(anaUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.declineReason").doesNotExist());
  }

  @Test
  void rejectsAnInvalidDeclineBodyWithoutChangingTheProposal() throws Exception {
    mockMvc
        .perform(
            postJson(proposal() + "/decline", Map.of("reason", "a".repeat(501))).with(as(anaUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post(proposal() + "/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": ")
                .with(as(anaUser)))
        .andExpect(status().isBadRequest());
    assertThat(proposalStatuses()).containsExactly("SENT");
    assertThat(requestStatus()).isEqualTo("ANSWERED");
  }

  @Test
  void asksForAnAdjustmentThatBothPartiesSee() throws Exception {
    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()).with(as(anaUser)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.adjustments.length()").value(1))
        .andExpect(jsonPath("$.adjustments[0].field").value("PRICE"))
        .andExpect(jsonPath("$.adjustments[0].suggestedValue").value("320,00"));
    mockMvc
        .perform(get("/api/drivers/me/proposals/" + proposalToken).with(as(carlos.getUser())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.adjustments[0].field").value("PRICE"));
  }

  @Test
  void rejectsAnInvalidAdjustmentWithoutSavingIt() throws Exception {
    Map<String, Object> withoutField = new HashMap<>(priceAdjustment());
    withoutField.remove("field");
    for (Map<String, ?> invalid :
        List.of(
            withoutField,
            Map.of("field", "COLOR", "note", "Outra cor"),
            Map.of("field", "PRICE", "suggestedValue", "1".repeat(101)),
            Map.of("field", "PRICE", "note", "a".repeat(501)))) {
      mockMvc
          .perform(postJson(proposal() + "/adjustments", invalid).with(as(anaUser)))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(post(proposal() + "/adjustments").with(as(anaUser)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            postJson(proposal() + "/adjustments", Map.of("field", "PRICE", "note", " "))
                .with(as(anaUser)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("proposal.adjustment.empty")));
    assertThat(adjustmentCount()).isZero();
  }

  @Test
  void anAnsweredProposalTakesNoOtherAnswer() throws Exception {
    mockMvc.perform(post(proposal() + "/decline").with(as(anaUser))).andExpect(status().isOk());

    mockMvc
        .perform(post(proposal() + "/accept").with(as(anaUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(message("proposal.not_current")));
    mockMvc
        .perform(post(proposal() + "/decline").with(as(anaUser)))
        .andExpect(status().isConflict());
    mockMvc
        .perform(postJson(proposal() + "/adjustments", priceAdjustment()).with(as(anaUser)))
        .andExpect(status().isConflict());
    assertThat(proposalStatuses()).containsExactly("DECLINED");
    assertThat(adjustmentCount()).isZero();
  }

  @Test
  void aProposalCannotBeEdited() throws Exception {
    mockMvc
        .perform(withJson(patch(proposal()), Map.of("totalAmount", "1.00")).with(as(anaUser)))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(withJson(put(proposal()), Map.of("totalAmount", "1.00")).with(as(anaUser)))
        .andExpect(status().isMethodNotAllowed());
    assertThat(proposalStatuses()).containsExactly("SENT");
  }

  private String chainOf(String token) {
    return REQUESTS + "/" + token + "/proposals";
  }

  private String proposal() {
    return PROPOSALS + "/" + proposalToken;
  }

  private Map<String, Object> priceAdjustment() {
    return Map.of("field", "PRICE", "suggestedValue", "320,00");
  }

  private Map<String, Object> termsOf(String json) throws Exception {
    Map<String, Object> proposal = objectMapper.readValue(json, new TypeReference<>() {});
    proposal.keySet().removeAll(List.of("status", "acceptedAt", "updatedAt"));
    return proposal;
  }

  private List<String> proposalStatuses() {
    return jdbc.queryForList("select status from proposal order by version", String.class);
  }

  private int adjustmentCount() {
    return jdbc.queryForObject("select count(*) from proposal_adjustment", Integer.class);
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
