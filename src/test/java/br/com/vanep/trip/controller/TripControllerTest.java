package br.com.vanep.trip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
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
class TripControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private TripRepository trips;

  private MockMvc mockMvc;
  private DriverModel owner;
  private String ownerUid;
  private String otherDriverUid;
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    owner = createDriver("owner@vanep.com", "11144477735");
    ownerUid = owner.getUser().getToken();
    otherDriverUid = createDriver("other@vanep.com", "52998224725").getUser().getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
  }

  @Test
  void createsTripAsScheduledWithoutStartedAt() throws Exception {
    mockMvc
        .perform(
            post("/api/trips")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(owner.getToken(), "MORNING")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.startedAt").doesNotExist())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void rejectsDuplicateActiveSlot() throws Exception {
    persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(
            post("/api/trips")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(owner.getToken(), "MORNING")))
        .andExpect(status().isConflict());
  }

  @Test
  void listingExcludesRemovedTrips() throws Exception {
    TripModel kept = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);
    TripModel removed = persistTrip(owner, TODAY, Shift.AFTERNOON, TripStatus.SCHEDULED);
    trips.delete(removed);

    mockMvc
        .perform(get("/api/trips").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(kept.getToken()));
  }

  @Test
  void owningDriverReadsOwnTripWithoutShowPermission() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(get("/api/trips/" + trip.getToken()).with(driverJwt(ownerUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(trip.getToken()));
  }

  @Test
  void anotherDriverCannotReadSomeoneElsesTrip() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(get("/api/trips/" + trip.getToken()).with(driverJwt(otherDriverUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void owningDriverCannotDeleteOwnTrip() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(delete("/api/trips/" + trip.getToken()).with(driverJwt(ownerUid)))
        .andExpect(status().isForbidden());

    assertThat(trips.findByToken(trip.getToken())).isPresent();
  }

  @Test
  void adminDeletesAndRestoresTrip() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(delete("/api/trips/" + trip.getToken()).with(adminJwt()))
        .andExpect(status().isNoContent());
    assertThat(trips.findByToken(trip.getToken())).isEmpty();

    mockMvc
        .perform(post("/api/trips/" + trip.getToken() + "/restore").with(adminJwt()))
        .andExpect(status().isOk());
    assertThat(trips.findByToken(trip.getToken())).isPresent();
  }

  @Test
  void adminReopensACompletedRoute() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.COMPLETED);
    trip.setStartedAt(Instant.parse("2026-09-10T09:00:00Z"));
    trip.setFinishedAt(Instant.parse("2026-09-10T12:00:00Z"));
    trips.save(trip);

    mockMvc
        .perform(
            patch("/api/trips/" + trip.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\",\"finishedAt\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.finishedAt").doesNotExist());
  }

  @Test
  void reopeningWithoutClearingTheFinishIsRefused() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.COMPLETED);
    trip.setStartedAt(Instant.parse("2026-09-10T09:00:00Z"));
    trip.setFinishedAt(Instant.parse("2026-09-10T12:00:00Z"));
    trips.save(trip);

    mockMvc
        .perform(
            patch("/api/trips/" + trip.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isBadRequest());

    assertThat(trips.findByToken(trip.getToken()).orElseThrow().getStatus())
        .isEqualTo(TripStatus.COMPLETED);
  }

  @Test
  void creatingACompletedTripWithoutTimestampsIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/trips")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"driverToken\":\""
                        + owner.getToken()
                        + "\",\"serviceDate\":\""
                        + TODAY
                        + "\",\"shift\":\"MORNING\",\"status\":\"COMPLETED\"}"))
        .andExpect(status().isBadRequest());

    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void creatingATripThatFinishedBeforeItStartedIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/trips")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"driverToken\":\""
                        + owner.getToken()
                        + "\",\"serviceDate\":\""
                        + TODAY
                        + "\",\"shift\":\"MORNING\",\"status\":\"COMPLETED\""
                        + ",\"startedAt\":\"2026-09-10T12:00:00Z\""
                        + ",\"finishedAt\":\"2026-09-10T09:00:00Z\"}"))
        .andExpect(status().isBadRequest());

    assertThat(trips.findAll()).isEmpty();
  }

  @Test
  void patchingOnlyStatusLeavesEveryOtherStoredFieldUnchanged() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.AFTERNOON, TripStatus.COMPLETED);
    Instant startedAt = Instant.parse("2026-09-10T09:00:00Z");
    Instant finishedAt = Instant.parse("2026-09-10T12:00:00Z");
    trip.setStartedAt(startedAt);
    trip.setFinishedAt(finishedAt);
    trips.save(trip);

    mockMvc
        .perform(
            patch("/api/trips/" + trip.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}"))
        .andExpect(status().isOk());

    TripModel stored = trips.findByToken(trip.getToken()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(TripStatus.CANCELLED);
    assertThat(stored.getShift()).isEqualTo(Shift.AFTERNOON);
    assertThat(stored.getStartedAt()).isEqualTo(startedAt);
    assertThat(stored.getFinishedAt()).isEqualTo(finishedAt);
  }

  @Test
  void clearingANonNullableFieldIsRefused() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(
            patch("/api/trips/" + trip.getToken())
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shift\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void responseExposesNoNumericIdentifier() throws Exception {
    TripModel trip = persistTrip(owner, TODAY, Shift.MORNING, TripStatus.SCHEDULED);

    mockMvc
        .perform(get("/api/trips/" + trip.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.driverId").doesNotExist())
        .andExpect(jsonPath("$.driverToken").value(owner.getToken()));
  }

  private String createBody(String driverToken, String shift) {
    return "{\"driverToken\":\""
        + driverToken
        + "\",\"serviceDate\":\""
        + TODAY
        + "\",\"shift\":\""
        + shift
        + "\"}";
  }

  private TripModel persistTrip(
      DriverModel driver, LocalDate serviceDate, Shift shift, TripStatus status) {
    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(serviceDate);
    trip.setShift(shift);
    trip.setStatus(status);
    return trips.save(trip);
  }

  private DriverModel createDriver(String email, String document) {
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
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(token -> token.claim("roles", List.of("ROLE_ADMIN")).subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_trips"),
            new SimpleGrantedAuthority("show_trip"),
            new SimpleGrantedAuthority("create_trip"),
            new SimpleGrantedAuthority("update_trip"),
            new SimpleGrantedAuthority("delete_trip"),
            new SimpleGrantedAuthority("restore_trip"));
  }

  private JwtRequestPostProcessor driverJwt(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_DRIVER")).subject(uid))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }
}
