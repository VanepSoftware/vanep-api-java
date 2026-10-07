package br.com.vanep.absence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.absence.repository.AbsenceRepository;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AbsenceNoShowControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientDriverRepository links;
  @Autowired private DependentRepository dependents;
  @Autowired private AbsenceRepository absences;
  @Autowired private TripRepository trips;
  @Autowired private AssistantRepository assistants;

  private MockMvc mockMvc;
  private DriverModel carlos;
  private DriverModel otherDriver;
  private DependentModel child;
  private TripModel liveTrip;
  private String carlosUid;
  private String otherDriverUid;
  private String mariaUid;
  private String assistantUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ClientModel maria = createClient("maria@vanep.com", "11144477735");
    mariaUid = maria.getUser().getToken();
    carlos = createDriver("carlos@vanep.com", "52998224725");
    carlosUid = carlos.getUser().getToken();
    otherDriver = createDriver("outro@vanep.com", "98765432100");
    otherDriverUid = otherDriver.getUser().getToken();
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(maria);
    link.setDriver(carlos);
    link.setStatus(RelationshipStatus.ACTIVE);
    links.save(link);
    child = new DependentModel();
    child.setClientId(maria.getId());
    child.setName("Aluno");
    child.setShift(Shift.MORNING);
    child = dependents.save(child);
    liveTrip =
        persistTrip(
            carlos, LocalDate.now(TripService.SERVICE_ZONE), Shift.MORNING, TripStatus.IN_PROGRESS);
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(
        createUser(UserType.ASSISTANT, "Assistente", "assistente@vanep.com", "15350946056"));
    assistant.setDriver(carlos);
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setActivatedAt(Instant.now());
    assistantUid = assistants.save(assistant).getUser().getToken();
  }

  @Test
  void driverRecordsNoShowAsDriver() throws Exception {
    mockMvc
        .perform(
            post(path(liveTrip))
                .with(operatorJwt(carlosUid, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "not at the stop")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].source").value("DRIVER"))
        .andExpect(jsonPath("$[0].leg").value("OUTBOUND"))
        .andExpect(jsonPath("$[0].reason").value("not at the stop"))
        .andExpect(jsonPath("$[0].tripToken").value(liveTrip.getToken()))
        .andExpect(jsonPath("$[0].id").doesNotExist());
  }

  @Test
  void assistantRecordsNoShowAsAssistant() throws Exception {
    mockMvc
        .perform(
            post(path(liveTrip))
                .with(operatorJwt(assistantUid, "ASSISTANT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "waited at the gate")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].source").value("ASSISTANT"));
  }

  @Test
  void otherDriverIsForbidden() throws Exception {
    mockMvc
        .perform(
            post(path(liveTrip))
                .with(operatorJwt(otherDriverUid, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "not at the stop")))
        .andExpect(status().isForbidden());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void blankReasonIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post(path(liveTrip))
                .with(operatorJwt(carlosUid, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "  ")))
        .andExpect(status().isBadRequest());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void completedTripIsConflict() throws Exception {
    TripModel done =
        persistTrip(
            carlos, LocalDate.now(TripService.SERVICE_ZONE), Shift.AFTERNOON, TripStatus.COMPLETED);
    mockMvc
        .perform(
            post(path(done))
                .with(operatorJwt(carlosUid, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "not at the stop")))
        .andExpect(status().isConflict());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void yesterdaysTripIsConflict() throws Exception {
    TripModel yesterday =
        persistTrip(
            carlos,
            LocalDate.now(TripService.SERVICE_ZONE).minusDays(1),
            Shift.MORNING,
            TripStatus.IN_PROGRESS);
    mockMvc
        .perform(
            post(path(yesterday))
                .with(operatorJwt(carlosUid, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "not at the stop")))
        .andExpect(status().isConflict());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void clientIsForbidden() throws Exception {
    mockMvc
        .perform(
            post(path(liveTrip))
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(child.getToken(), "not at the stop")))
        .andExpect(status().isForbidden());
    assertThat(absences.findAll()).isEmpty();
  }

  private String path(TripModel trip) {
    return "/api/trips/" + trip.getToken() + "/no-shows";
  }

  private String body(String dependentToken, String reason) {
    return "{\"dependentToken\":\"" + dependentToken + "\",\"reason\":\"" + reason + "\"}";
  }

  private TripModel persistTrip(
      DriverModel driver, LocalDate date, Shift shift, TripStatus status) {
    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(date);
    trip.setShift(shift);
    trip.setStatus(status);
    if (status == TripStatus.IN_PROGRESS || status == TripStatus.COMPLETED) {
      trip.setStartedAt(Instant.now());
    }
    if (status == TripStatus.COMPLETED) {
      trip.setFinishedAt(Instant.now());
    }
    return trips.save(trip);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, "Cliente", email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Motorista", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
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

  private JwtRequestPostProcessor operatorJwt(String uid, String role) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_" + role)).subject(uid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_" + role),
            new SimpleGrantedAuthority("report_no_show"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", mariaUid)
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject(mariaUid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("report_absence"));
  }
}
