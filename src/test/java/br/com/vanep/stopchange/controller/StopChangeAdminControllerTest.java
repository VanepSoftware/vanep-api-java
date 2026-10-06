package br.com.vanep.stopchange.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class StopChangeAdminControllerTest {

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

  private String adminUid;
  private String clientUid;
  private String driverUid;
  private String otherClientUid;

  private StopChangeRequestModel requestModel;

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
    city.setName("São Paulo");
    city.setState(state);
    city = cities.save(city);

    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setStreet("Avenida Paulista");
    address.setNumber("1000");
    address.setZipCode("01310100");
    address.setActive(true);
    address = addresses.save(address);

    UserModel adminUser = createUser("admin@vanep.com", "44455566677", UserType.ADMIN);
    adminUid = adminUser.getToken();

    UserModel clientUser = createUser("client@vanep.com", "11122233344", UserType.CLIENT);
    clientUid = clientUser.getToken();

    ClientModel client = new ClientModel();
    client.setUser(clientUser);
    client.setRating(BigDecimal.valueOf(5.0));
    client = clients.save(client);

    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName("Enzo Silva");
    dependent.setDocument("12345678901");
    dependent.setShift(Shift.MORNING);
    dependent.setDefaultDependent(true);
    dependent = dependents.save(dependent);

    UserModel driverUser = createUser("driver@vanep.com", "99988877766", UserType.DRIVER);
    driverUid = driverUser.getToken();

    DriverModel driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    driver.setBasePrice(new BigDecimal("150.00"));
    driver = drivers.save(driver);

    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(LocalDate.now(SAO_PAULO_ZONE));
    trip.setShift(Shift.MORNING);
    trip.setStatus(TripStatus.SCHEDULED);
    trip = trips.save(trip);

    UserModel otherUser = createUser("other@vanep.com", "77788899900", UserType.CLIENT);
    otherClientUid = otherUser.getToken();

    ClientModel otherClient = new ClientModel();
    otherClient.setUser(otherUser);
    otherClient.setRating(BigDecimal.valueOf(5.0));
    clients.save(otherClient);

    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setDependent(dependent);
    req.setTrip(trip);
    req.setServiceDate(trip.getServiceDate());
    req.setRequestedByUser(clientUser);
    req.setNewDropoffAddress(address);
    req.setReason("Médico às 14h");
    req.setStatus(StopChangeStatus.PENDING);
    requestModel = stopChangeRequests.save(req);
  }

  private UserModel createUser(String email, String doc, UserType type) {
    UserModel user = new UserModel();
    user.setName(email.split("@")[0]);
    user.setEmail(email);
    user.setDocument(doc);
    user.setType(type);
    user.setPassword("secret");
    return users.save(user);
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(builder -> builder.subject(adminUid).claim("uid", adminUid))
        .authorities(
            new SimpleGrantedAuthority("list_stop_change_requests"),
            new SimpleGrantedAuthority("show_stop_change_request"),
            new SimpleGrantedAuthority("delete_stop_change_request"),
            new SimpleGrantedAuthority("restore_stop_change_request"));
  }

  private JwtRequestPostProcessor clientJwt(String uid) {
    return jwt()
        .jwt(builder -> builder.subject(uid).claim("uid", uid))
        .authorities(
            new SimpleGrantedAuthority("request_stop_change"),
            new SimpleGrantedAuthority("cancel_stop_change"));
  }

  private JwtRequestPostProcessor driverJwt(String uid) {
    return jwt()
        .jwt(builder -> builder.subject(uid).claim("uid", uid))
        .authorities(
            new SimpleGrantedAuthority("approve_stop_change"),
            new SimpleGrantedAuthority("reject_stop_change"));
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/stop-change-requests")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanListStopChangeRequestsPaginated() throws Exception {
    mockMvc
        .perform(get("/api/stop-change-requests").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(requestModel.getToken()))
        .andExpect(jsonPath("$.content[0].reason").value("Médico às 14h"))
        .andExpect(jsonPath("$.content[0].status").value("PENDING"));
  }

  @Test
  void nonAdminCannotListStopChangeRequests() throws Exception {
    mockMvc
        .perform(get("/api/stop-change-requests").with(clientJwt(clientUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanGetByToken() throws Exception {
    mockMvc
        .perform(get("/api/stop-change-requests/" + requestModel.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(requestModel.getToken()))
        .andExpect(jsonPath("$.reason").value("Médico às 14h"))
        .andExpect(jsonPath("$.dependentToken").isNotEmpty())
        .andExpect(jsonPath("$.tripToken").isNotEmpty());
  }

  @Test
  void requesterClientCanGetByToken() throws Exception {
    mockMvc
        .perform(
            get("/api/stop-change-requests/" + requestModel.getToken()).with(clientJwt(clientUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(requestModel.getToken()));
  }

  @Test
  void assignedDriverCanGetByToken() throws Exception {
    mockMvc
        .perform(
            get("/api/stop-change-requests/" + requestModel.getToken()).with(driverJwt(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(requestModel.getToken()));
  }

  @Test
  void unrelatedUserCannotGetByToken() throws Exception {
    mockMvc
        .perform(
            get("/api/stop-change-requests/" + requestModel.getToken())
                .with(clientJwt(otherClientUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanSoftDeleteAndRestore() throws Exception {
    mockMvc
        .perform(delete("/api/stop-change-requests/" + requestModel.getToken()).with(adminJwt()))
        .andExpect(status().isNoContent());

    // After soft delete, querying by token returns 404
    mockMvc
        .perform(get("/api/stop-change-requests/" + requestModel.getToken()).with(adminJwt()))
        .andExpect(status().isNotFound());

    // Listing excludes it
    mockMvc
        .perform(get("/api/stop-change-requests").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    // Admin can restore it
    mockMvc
        .perform(
            post("/api/stop-change-requests/" + requestModel.getToken() + "/restore")
                .with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(requestModel.getToken()))
        .andExpect(jsonPath("$.status").value("PENDING"));

    // Now query succeeds again
    mockMvc
        .perform(get("/api/stop-change-requests/" + requestModel.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(requestModel.getToken()));
  }

  @Test
  void nonAdminCannotDelete() throws Exception {
    mockMvc
        .perform(
            delete("/api/stop-change-requests/" + requestModel.getToken())
                .with(clientJwt(clientUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void nonAdminCannotRestore() throws Exception {
    mockMvc
        .perform(
            post("/api/stop-change-requests/" + requestModel.getToken() + "/restore")
                .with(clientJwt(clientUid)))
        .andExpect(status().isForbidden());
  }
}
