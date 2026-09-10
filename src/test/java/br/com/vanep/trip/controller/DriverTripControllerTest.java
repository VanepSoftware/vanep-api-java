package br.com.vanep.trip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
class DriverTripControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String MORNING = "{\"shift\":\"MORNING\"}";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private TripRepository trips;

  private MockMvc mockMvc;
  private String approvedUid;
  private String pendingUid;
  private String clientUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    approvedUid = createDriver("approved@vanep.com", "11144477735", DriverApprovalStatus.APPROVED);
    pendingUid = createDriver("pending@vanep.com", "52998224725", DriverApprovalStatus.PENDING);

    UserModel client = new UserModel();
    client.setType(UserType.CLIENT);
    client.setName("Cliente");
    client.setEmail("client@vanep.com");
    client.setDocument("15350946056");
    client.setVerified(true);
    client.setTermsAcceptedAt(Instant.now());
    clientUid = users.save(client).getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/drivers/me/trips/today")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void clientCannotStartARoute() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isForbidden());
    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void pendingDriverCannotStartARoute() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(pendingUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isForbidden());
    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void startsTheRouteAndBecomesInProgress() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.startedAt").isNotEmpty());
  }

  @Test
  void startingTwiceIsIdempotentAndKeepsTheFirstStartedAt() throws Exception {
    JsonNode first = MAPPER.readTree(start(approvedUid));
    Instant storedAfterFirstStart = trips.findAll().getFirst().getStartedAt();

    JsonNode second = MAPPER.readTree(start(approvedUid));

    assertThat(second.get("token").asText()).isEqualTo(first.get("token").asText());
    assertThat(trips.findAll()).hasSize(1);
    assertThat(trips.findAll().getFirst().getStartedAt()).isEqualTo(storedAfterFirstStart);
  }

  @Test
  void finishingWithoutStartingIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/finish")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isConflict());
  }

  @Test
  void finishesTheRouteAndBecomesCompleted() throws Exception {
    start(approvedUid);

    mockMvc
        .perform(
            post("/api/drivers/me/trips/finish")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.finishedAt").isNotEmpty());
  }

  @Test
  void todayReturnsAnEmptyListWhenNothingWasStarted() throws Exception {
    mockMvc
        .perform(get("/api/drivers/me/trips/today").with(driverJwt(approvedUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void todayReturnsBothShiftsInCreationOrder() throws Exception {
    start(approvedUid);
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shift\":\"AFTERNOON\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/drivers/me/trips/today").with(driverJwt(approvedUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].shift").value("MORNING"))
        .andExpect(jsonPath("$[1].shift").value("AFTERNOON"));
  }

  @Test
  void startingOutsideTheConfiguredWindowIsRecordedNotBlocked() throws Exception {
    DriverModel driver =
        drivers.findAll().stream()
            .filter(d -> d.getApprovalStatus() == DriverApprovalStatus.APPROVED)
            .findFirst()
            .orElseThrow();
    driver.setWorkDays(List.of("MONDAY"));
    driver.setWorkStartTime(LocalTime.of(7, 0));
    driver.setWorkEndTime(LocalTime.of(7, 1));
    drivers.save(driver);

    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.outsideWorkWindow").value(true));
  }

  @Test
  void anInvalidShiftIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(approvedUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shift\":\"MADRUGADA\"}"))
        .andExpect(status().isBadRequest());
  }

  private String start(String uid) throws Exception {
    return mockMvc
        .perform(
            post("/api/drivers/me/trips/start")
                .with(driverJwt(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MORNING))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String createDriver(String email, String document, DriverApprovalStatus status) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(BigDecimal.valueOf(50));
    driver.setApprovalStatus(status);
    drivers.save(driver);
    return user.getToken();
  }

  private JwtRequestPostProcessor driverJwt(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_DRIVER")).subject(uid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("start_trip"),
            new SimpleGrantedAuthority("finish_trip"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", clientUid)
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject(clientUid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("start_trip"),
            new SimpleGrantedAuthority("finish_trip"));
  }
}
