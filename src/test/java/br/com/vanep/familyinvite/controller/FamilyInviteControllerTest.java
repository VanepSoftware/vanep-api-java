package br.com.vanep.familyinvite.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.familyinvite.StudentFamilyFixtures;
import br.com.vanep.school.model.SchoolModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class FamilyInviteControllerTest {

  private static final String PASSENGERS = "/api/drivers/me/unlinked-passengers";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private StudentFamilyFixtures fixtures;
  @Autowired private ClientDriverRepository links;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private CityModel city;
  private SchoolModel school;
  private DriverModel carlos;
  private String carlosUid;
  private String joanaUid;
  private ClientModel maria;
  private String mariaUid;
  private DependentModel rafael;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    city = fixtures.createCity();
    school = fixtures.createSchool(city, "Colégio Horizonte");
    carlos = fixtures.createDriver("Carlos Pereira", "carlos@vanep.com", "52998224725");
    carlosUid = carlos.getUser().getToken();
    joanaUid =
        fixtures.createDriver("Joana Lima", "joana@vanep.com", "11144477735").getUser().getToken();
    maria = fixtures.createClient("Maria Costa", "maria@vanep.com", "12345678909");
    mariaUid = maria.getUser().getToken();
    rafael =
        fixtures.createDependent(
            maria, "Rafael Costa", school, city, LocalDate.now().minusYears(9).minusDays(3));
  }

  @Test
  void aDriverInvitesTheFamilyAndResendingKeepsTheLink() throws Exception {
    String passenger = createPassenger("Rafael");

    JsonNode first = invite(passenger);
    JsonNode second = invite(passenger);

    assertThat(first.get("inviteUrl").asText()).endsWith("/" + first.get("code").asText());
    assertThat(second.get("code").asText()).isEqualTo(first.get("code").asText());
    assertThat(second.get("sentAt").asText()).isGreaterThanOrEqualTo(first.get("sentAt").asText());
    String passengerView =
        mockMvc
            .perform(get(PASSENGERS + "/" + passenger).with(as(carlosUid)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.invitedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // The invite response still has Instant.now() nanos; invitedAt is that value read back from
    // timestamptz, which keeps microseconds.
    assertThat(Instant.parse(objectMapper.readTree(passengerView).get("invitedAt").asText()))
        .isCloseTo(Instant.parse(second.get("sentAt").asText()), within(1, ChronoUnit.MICROS));
  }

  @Test
  void anotherDriverCannotInviteForAPassengerThatIsNotTheirs() throws Exception {
    String passenger = createPassenger("Rafael");

    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(joanaUid)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(mariaUid)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/invite"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theGroupLinkIsCreatedOnceAndReused() throws Exception {
    String first = groupLinkCode();
    String second = groupLinkCode();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void anyoneSeesAMinimalPreviewOfAPassengerInvite() throws Exception {
    String code = invite(createPassenger("Rafael Costa")).get("code").asText();

    String body =
        mockMvc
            .perform(get("/api/family-invites/" + code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.driverFirstName").value("Carlos"))
            .andExpect(jsonPath("$.schoolName").value("Colégio Horizonte"))
            .andExpect(jsonPath("$.studentFirstName").value("Rafael"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("Rua", "06:40", "13015904", "Costa");
  }

  @Test
  void anUnknownCodeIsNotFound() throws Exception {
    mockMvc.perform(get("/api/family-invites/NOPE")).andExpect(status().isNotFound());
    mockMvc
        .perform(
            postJson("/api/family-invites/NOPE/accept", Map.of("dependentToken", rafael.getToken()))
                .with(as(mariaUid)))
        .andExpect(status().isNotFound());
  }

  @Test
  void acceptingCreatesAPendingLinkAndAMatchTheDriverSees() throws Exception {
    String passenger = createPassenger("Rafael");
    String code = invite(passenger).get("code").asText();

    JsonNode accepted = accept(code, rafael.getToken());

    ClientDriverModel link = links.findByPair(maria.getId(), carlos.getId()).orElseThrow();
    assertThat(link.getStatus()).isEqualTo(RelationshipStatus.PENDING);
    assertThat(link.getOrigin()).isEqualTo(LinkOrigin.DRIVER_INVITE);
    assertThat(accepted.get("clientDriverToken").asText()).isEqualTo(link.getToken());
    mockMvc
        .perform(get(PASSENGERS + "/" + passenger).with(as(carlosUid)))
        .andExpect(jsonPath("$.familyMatch.token").value(accepted.get("familyMatchToken").asText()))
        .andExpect(jsonPath("$.familyMatch.dependentName").value("Rafael Costa"))
        .andExpect(jsonPath("$.familyMatch.dependentAge").value(9))
        .andExpect(jsonPath("$.familyMatch.clientName").value("Maria Costa"));
  }

  @Test
  void acceptingTwiceIsIdempotent() throws Exception {
    String code = invite(createPassenger("Rafael")).get("code").asText();

    JsonNode first = accept(code, rafael.getToken());
    JsonNode second = accept(code, rafael.getToken());

    assertThat(second.get("familyMatchToken").asText())
        .isEqualTo(first.get("familyMatchToken").asText());
    assertThat(jdbc.queryForObject("select count(*) from family_match", Long.class)).isEqualTo(1L);
  }

  @Test
  void anotherClientsDependentIsNotFoundAndNothingIsCreated() throws Exception {
    ClientModel ana = fixtures.createClient("Ana Souza", "ana@vanep.com", "98765432100");
    String code = invite(createPassenger("Rafael")).get("code").asText();

    mockMvc
        .perform(
            postJson(
                    "/api/family-invites/" + code + "/accept",
                    Map.of("dependentToken", rafael.getToken()))
                .with(as(ana.getUser().getToken())))
        .andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject("select count(*) from family_match", Long.class)).isZero();
    assertThat(links.findByPair(ana.getId(), carlos.getId())).isEmpty();
  }

  @Test
  void onlyAClientAcceptsAndTheDependentIsRequired() throws Exception {
    String code = invite(createPassenger("Rafael")).get("code").asText();

    mockMvc
        .perform(
            postJson(
                    "/api/family-invites/" + code + "/accept",
                    Map.of("dependentToken", rafael.getToken()))
                .with(as(joanaUid)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(postJson("/api/family-invites/" + code + "/accept", Map.of()).with(as(mariaUid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theGroupLinkSuggestsTheOnlyPassengerWithTheSameFirstNameAtTheSchool() throws Exception {
    String passenger = createPassenger("rafaél");
    createPassenger("Laura");

    JsonNode accepted = accept(groupLinkCode(), rafael.getToken());

    assertThat(accepted.get("familyMatchToken").isNull()).isFalse();
    mockMvc
        .perform(get(PASSENGERS + "/" + passenger).with(as(carlosUid)))
        .andExpect(jsonPath("$.familyMatch.dependentName").value("Rafael Costa"));
  }

  @Test
  void theGroupLinkSuggestsNothingWhenTwoPassengersShareTheFirstName() throws Exception {
    createPassenger("Rafael Lima");
    createPassenger("Rafael Souza");

    JsonNode accepted = accept(groupLinkCode(), rafael.getToken());

    assertThat(accepted.get("familyMatchToken").isNull()).isTrue();
    assertThat(links.findByPair(maria.getId(), carlos.getId())).isPresent();
  }

  @Test
  void mergingKeepsThePassengerAndTakesTheDependentsName() throws Exception {
    String passenger = createPassenger("Rafa");
    accept(invite(passenger).get("code").asText(), rafael.getToken());

    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/merge").with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(passenger))
        .andExpect(jsonPath("$.name").value("Rafael Costa"))
        .andExpect(jsonPath("$.dependentToken").value(rafael.getToken()))
        .andExpect(jsonPath("$.familyMatch").doesNotExist())
        .andExpect(jsonPath("$.slots.length()").value(2));

    mockMvc
        .perform(get("/api/drivers/me/occupancy").with(as(carlosUid)))
        .andExpect(jsonPath("$.students").value(1))
        .andExpect(jsonPath("$.studentsWithFamily").value(1));
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(carlosUid)))
        .andExpect(status().isConflict());
  }

  @Test
  void mergingWithoutAPendingMatchIsAConflict() throws Exception {
    String passenger = createPassenger("Rafael");

    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/merge").with(as(carlosUid)))
        .andExpect(status().isConflict());
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/family-match/dismiss").with(as(carlosUid)))
        .andExpect(status().isConflict());
  }

  @Test
  void dismissingKeepsTheInviteAndANewAcceptanceSuggestsAgain() throws Exception {
    String passenger = createPassenger("Rafael");
    String code = invite(passenger).get("code").asText();
    accept(code, rafael.getToken());

    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/family-match/dismiss").with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.familyMatch").doesNotExist())
        .andExpect(jsonPath("$.invitedAt").isNotEmpty());

    accept(code, rafael.getToken());
    mockMvc
        .perform(get(PASSENGERS + "/" + passenger).with(as(carlosUid)))
        .andExpect(jsonPath("$.familyMatch.dependentName").value("Rafael Costa"));
  }

  @Test
  void anotherDriverCannotMergeOrDismiss() throws Exception {
    String passenger = createPassenger("Rafael");
    accept(invite(passenger).get("code").asText(), rafael.getToken());

    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/merge").with(as(joanaUid)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/family-match/dismiss").with(as(joanaUid)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletingThePassengerDeletesItsInvite() throws Exception {
    String passenger = createPassenger("Rafael");
    String code = invite(passenger).get("code").asText();

    mockMvc
        .perform(delete(PASSENGERS + "/" + passenger).with(as(carlosUid)))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/family-invites/" + code)).andExpect(status().isNotFound());
  }

  private String createPassenger(String name) throws Exception {
    String body =
        mockMvc
            .perform(
                postJson(PASSENGERS, fixtures.passengerRequest(name, school, city, null))
                    .with(as(carlosUid)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  private JsonNode invite(String passenger) throws Exception {
    String body =
        mockMvc
            .perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(carlosUid)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private String groupLinkCode() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/drivers/me/invite-link").with(as(carlosUid)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("code").asText();
  }

  private JsonNode accept(String code, String dependentToken) throws Exception {
    String body =
        mockMvc
            .perform(
                postJson(
                        "/api/family-invites/" + code + "/accept",
                        Map.of("dependentToken", dependentToken))
                    .with(as(mariaUid)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt().jwt(token -> token.claim("uid", uid).subject(uid));
  }
}
