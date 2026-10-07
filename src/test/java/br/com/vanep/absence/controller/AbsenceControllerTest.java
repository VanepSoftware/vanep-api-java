package br.com.vanep.absence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.model.AbsenceModel;
import br.com.vanep.absence.repository.AbsenceRepository;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AbsenceControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientDriverRepository links;
  @Autowired private DependentRepository dependents;
  @Autowired private AbsenceRepository absences;
  @Autowired private TripRepository trips;

  private MockMvc mockMvc;
  private ClientModel maria;
  private ClientModel bruno;
  private DriverModel carlos;
  private ClientDriverModel activeLink;
  private DependentModel fulltimeChild;
  private DependentModel morningChild;
  private DependentModel brunoChild;
  private String mariaUid;
  private String carlosUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    maria = createClient("maria@vanep.com", "11144477735");
    mariaUid = maria.getUser().getToken();
    bruno = createClient("bruno@vanep.com", "12345678909");
    carlos = createDriver("carlos@vanep.com", "52998224725");
    carlosUid = carlos.getUser().getToken();
    activeLink = persistLink(maria, carlos, RelationshipStatus.ACTIVE);
    fulltimeChild = persistDependent(maria.getId(), Shift.FULLTIME);
    morningChild = persistDependent(maria.getId(), Shift.MORNING);
    brunoChild = persistDependent(bruno.getId(), Shift.FULLTIME);
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void driverJwtIsForbiddenOnTheClientEndpoint() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isForbidden());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void clientReportsOutboundAndGets201() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").isNotEmpty())
        .andExpect(jsonPath("$[0].leg").value("OUTBOUND"))
        .andExpect(jsonPath("$[0].source").value("CLIENT"))
        .andExpect(jsonPath("$[0].dependentToken").value(fulltimeChild.getToken()));
  }

  @Test
  void repeatingOutboundReturns200WithTheSameToken() throws Exception {
    String first =
        mockMvc
            .perform(
                post(path(activeLink))
                    .with(clientJwt(mariaUid))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(fulltimeChild.getToken(), "OUTBOUND")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mockMvc
            .perform(
                post(path(activeLink))
                    .with(clientJwt(mariaUid))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(fulltimeChild.getToken(), "OUTBOUND")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode firstToken = MAPPER.readTree(first).get(0).get("token");
    JsonNode secondToken = MAPPER.readTree(second).get(0).get("token");
    assertThat(secondToken.asText()).isEqualTo(firstToken.asText());
    assertThat(absences.findAll()).hasSize(1);
  }

  @Test
  void bothOnFulltimeCreatesTwoItems() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "BOTH")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].leg").value("OUTBOUND"))
        .andExpect(jsonPath("$[1].leg").value("RETURN"));
  }

  @Test
  void bothOnMorningIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(morningChild.getToken(), "BOTH")))
        .andExpect(status().isBadRequest());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void inactiveLinkIsConflict() throws Exception {
    DriverModel otherDriver = createDriver("outro@vanep.com", "98765432100");
    ClientDriverModel inactive = persistLink(maria, otherDriver, RelationshipStatus.INACTIVE);
    mockMvc
        .perform(
            post(path(inactive))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isConflict());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void anotherClientsDependentIsForbidden() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(brunoChild.getToken(), "OUTBOUND")))
        .andExpect(status().isForbidden());
    assertThat(absences.findAll()).isEmpty();
  }

  @Test
  void listsTodaysAbsences() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get(path(activeLink) + "/today")
                .param("dependentToken", fulltimeChild.getToken())
                .with(clientJwt(mariaUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].leg").value("OUTBOUND"));
  }

  @Test
  void undoReturns204AndRemovesTheRow() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            delete(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get(path(activeLink) + "/today")
                .param("dependentToken", fulltimeChild.getToken())
                .with(clientJwt(mariaUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void undoIsConflictWhenTheTripIsInProgress() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isCreated());

    AbsenceModel row =
        absences
            .findByDependentAndDateAndLeg(
                fulltimeChild.getId(), LocalDate.now(TripService.SERVICE_ZONE), AbsenceLeg.OUTBOUND)
            .orElseThrow();
    TripModel trip = new TripModel();
    trip.setDriver(carlos);
    trip.setShift(Shift.MORNING);
    trip.setStatus(TripStatus.IN_PROGRESS);
    trip.setServiceDate(LocalDate.now(TripService.SERVICE_ZONE));
    trip.setStartedAt(Instant.now());
    trip = trips.save(trip);
    row.setTrip(trip);
    absences.save(row);

    mockMvc
        .perform(
            delete(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isConflict());
    assertThat(
            absences.findByDependentAndDateAndLeg(
                fulltimeChild.getId(),
                LocalDate.now(TripService.SERVICE_ZONE),
                AbsenceLeg.OUTBOUND))
        .isPresent();
  }

  @Test
  void responseExposesNoNumericIdentifier() throws Exception {
    mockMvc
        .perform(
            post(path(activeLink))
                .with(clientJwt(mariaUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fulltimeChild.getToken(), "OUTBOUND")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].id").doesNotExist())
        .andExpect(jsonPath("$[0].dependentId").doesNotExist())
        .andExpect(jsonPath("$[0].clientDriverId").doesNotExist())
        .andExpect(jsonPath("$[0].tripId").doesNotExist())
        .andExpect(jsonPath("$[0].token").isNotEmpty());
  }

  private String path(ClientDriverModel link) {
    return "/api/client-drivers/" + link.getToken() + "/absences";
  }

  private String body(String dependentToken, String scope) {
    return "{\"dependentToken\":\"" + dependentToken + "\",\"scope\":\"" + scope + "\"}";
  }

  private ClientDriverModel persistLink(
      ClientModel client, DriverModel driver, RelationshipStatus status) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setStatus(status);
    return links.save(link);
  }

  private DependentModel persistDependent(Long clientId, Shift shift) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(clientId);
    dependent.setName("Aluno");
    dependent.setShift(shift);
    return dependents.save(dependent);
  }

  private ClientModel createClient(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Cliente");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    ClientModel client = new ClientModel();
    client.setUser(users.save(user));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    DriverModel driver = new DriverModel();
    driver.setUser(users.save(user));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
  }

  private JwtRequestPostProcessor clientJwt(String uid) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_CLIENT")).subject(uid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("report_absence"));
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(
            token ->
                token
                    .claim("uid", carlosUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(carlosUid))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"), new SimpleGrantedAuthority("start_trip"));
  }
}
