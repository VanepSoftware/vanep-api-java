package br.com.vanep.stopchange.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
import br.com.vanep.stopchange.repository.StopChangeRequestRepository;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class StopChangeClientDriverControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DependentRepository dependents;
  @Autowired private DriverRepository drivers;
  @Autowired private TripRepository trips;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private StopChangeRequestRepository stopChangeRequests;

  private MockMvc mockMvc;

  private String clientUid;
  private String driverUid;
  private String otherClientUid;

  private ClientModel client;
  private DependentModel dependent;
  private DriverModel driver;
  private TripModel trip;
  private AddressModel address;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    city = cities.save(city);

    address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua da Vovó");
    address.setNumber("123");
    address = addresses.save(address);

    UserModel clientUser = createUser("client@vanep.com", "11144477735", UserType.CLIENT);
    clientUid = clientUser.getToken();

    client = new ClientModel();
    client.setUser(clientUser);
    client = clients.save(client);

    dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName("Filho do Cliente");
    dependent.setShift(Shift.MORNING);
    dependent = dependents.save(dependent);

    UserModel driverUser = createUser("driver@vanep.com", "22233344455", UserType.DRIVER);
    driverUid = driverUser.getToken();

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("150.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver = drivers.save(driver);

    LocalDate today = LocalDate.now(SAO_PAULO_ZONE);
    trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(today);
    trip.setShift(Shift.MORNING);
    trip.setStatus(TripStatus.IN_PROGRESS);
    trip = trips.save(trip);

    UserModel otherUser = createUser("other@vanep.com", "33344455566", UserType.CLIENT);
    otherClientUid = otherUser.getToken();
  }

  @Test
  void rejectsUnauthenticatedRequests() throws Exception {
    mockMvc
        .perform(get("/api/clients/me/stop-change-requests/today"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(get("/api/drivers/me/stop-change-requests/today"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void clientRequestsStopChangeSuccessfully() throws Exception {
    String body =
        """
        {
          "dependentToken": "%s",
          "tripToken": "%s",
          "addressToken": "%s",
          "reason": "Deixar na casa da vovó hoje"
        }
        """
            .formatted(dependent.getToken(), trip.getToken(), address.getToken());

    String response =
        mockMvc
            .perform(
                post("/api/clients/me/stop-change-requests")
                    .with(clientJwt(clientUid))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.dependentToken").value(dependent.getToken()))
            .andExpect(jsonPath("$.tripToken").value(trip.getToken()))
            .andExpect(jsonPath("$.reason").value("Deixar na casa da vovó hoje"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode node = MAPPER.readTree(response);
    assertThat(node.has("id")).isFalse();
    assertThat(node.has("dependentId")).isFalse();
    assertThat(node.has("tripId")).isFalse();
  }

  @Test
  void clientListsTodayRequests() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    stopChangeRequests.save(req);

    mockMvc
        .perform(get("/api/clients/me/stop-change-requests/today").with(clientJwt(clientUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].token").value(req.getToken()))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  @Test
  void clientCancelsPendingRequest() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    req = stopChangeRequests.save(req);

    mockMvc
        .perform(
            post("/api/clients/me/stop-change-requests/" + req.getToken() + "/cancel")
                .with(clientJwt(clientUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(req.getToken()))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void driverListsTodayRequests() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    stopChangeRequests.save(req);

    mockMvc
        .perform(get("/api/drivers/me/stop-change-requests/today").with(driverJwt(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].token").value(req.getToken()));
  }

  @Test
  void driverApprovesPendingRequest() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    req = stopChangeRequests.save(req);

    mockMvc
        .perform(
            post("/api/drivers/me/stop-change-requests/" + req.getToken() + "/approve")
                .with(driverJwt(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(req.getToken()))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.respondedByUserToken").value(driverUid))
        .andExpect(jsonPath("$.respondedAt").isNotEmpty());
  }

  @Test
  void driverRejectsPendingRequest() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    req = stopChangeRequests.save(req);

    mockMvc
        .perform(
            post("/api/drivers/me/stop-change-requests/" + req.getToken() + "/reject")
                .with(driverJwt(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(req.getToken()))
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.respondedByUserToken").value(driverUid));
  }

  @Test
  void clientCannotApproveRequest() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    req = stopChangeRequests.save(req);

    mockMvc
        .perform(
            post("/api/drivers/me/stop-change-requests/" + req.getToken() + "/approve")
                .with(clientJwt(clientUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void otherClientCannotCancelRequest() throws Exception {
    StopChangeRequestModel req = createExistingRequest(dependent, trip, client.getUser(), address);
    req = stopChangeRequests.save(req);

    mockMvc
        .perform(
            post("/api/clients/me/stop-change-requests/" + req.getToken() + "/cancel")
                .with(clientJwt(otherClientUid)))
        .andExpect(status().isForbidden());
  }

  private StopChangeRequestModel createExistingRequest(
      DependentModel dep, TripModel t, UserModel requester, AddressModel addr) {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setDependent(dep);
    req.setTrip(t);
    req.setServiceDate(t.getServiceDate());
    req.setRequestedByUser(requester);
    req.setNewDropoffAddress(addr);
    req.setReason("Motivo de teste");
    req.setStatus(StopChangeStatus.PENDING);
    return req;
  }

  private UserModel createUser(String email, String document, UserType type) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("User " + type);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private JwtRequestPostProcessor clientJwt(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_CLIENT")).subject(uid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("request_stop_change"),
            new SimpleGrantedAuthority("cancel_stop_change"));
  }

  private JwtRequestPostProcessor driverJwt(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_DRIVER")).subject(uid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("approve_stop_change"),
            new SimpleGrantedAuthority("reject_stop_change"));
  }
}
