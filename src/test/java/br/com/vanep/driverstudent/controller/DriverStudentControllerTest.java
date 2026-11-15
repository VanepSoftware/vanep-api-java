package br.com.vanep.driverstudent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import br.com.vanep.driverstudent.service.DriverStudentService;
import br.com.vanep.familyinvite.StudentFamilyFixtures;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.vehicle.model.VehicleModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverStudentControllerTest {

  private static final String STUDENTS = "/api/drivers/me/students";
  private static final String PASSENGERS = "/api/drivers/me/unlinked-passengers";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private StudentFamilyFixtures fixtures;
  @Autowired private ClientDriverRepository links;
  @Autowired private DriverStudentService service;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private MockMvc mockMvc;
  private CityModel city;
  private SchoolModel school;
  private DriverModel carlos;
  private String carlosUid;
  private ClientModel maria;
  private DependentModel bruno;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    city = fixtures.createCity();
    school = fixtures.createSchool(city, "Colégio Horizonte");
    carlos = fixtures.createDriver("Carlos Pereira", "carlos@vanep.com", "52998224725");
    carlosUid = carlos.getUser().getToken();
    maria = fixtures.createClient("Maria Costa", "maria@vanep.com", "12345678909");
    bruno = fixtures.createDependent(maria, "Bruno Costa", school, city, null);
  }

  @Test
  void listsContractedAndUnlinkedStudentsTogetherByName() throws Exception {
    createActiveContract(bruno);
    createPassenger("Laura Mendes", null);
    createPassenger("Ana Lima", null);

    mockMvc
        .perform(get(STUDENTS).with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("Ana Lima"))
        .andExpect(jsonPath("$[0].source").value("UNLINKED_PASSENGER"))
        .andExpect(jsonPath("$[0].familyStatus").value("NOT_INVITED"))
        .andExpect(jsonPath("$[0].schoolShift").value("MORNING"))
        .andExpect(jsonPath("$[0].slots.length()").value(2))
        .andExpect(jsonPath("$[1].name").value("Bruno Costa"))
        .andExpect(jsonPath("$[1].source").value("CONTRACT_ITEM"))
        .andExpect(jsonPath("$[1].familyStatus").value("WITH_FAMILY"))
        .andExpect(jsonPath("$[1].dependentToken").value(bruno.getToken()))
        .andExpect(jsonPath("$[1].slots[0].leg").value("OUTBOUND"))
        .andExpect(jsonPath("$[2].name").value("Laura Mendes"));
  }

  @Test
  void eachFamilyStatusFollowsTheInviteAndTheMatch() throws Exception {
    String invited = createPassenger("Sofia Lima", null);
    String joined = createPassenger("Bruno", null);
    mockMvc
        .perform(post(PASSENGERS + "/" + invited + "/invite").with(as(carlosUid)))
        .andExpect(status().isOk());
    String code = inviteCode(joined);
    mockMvc
        .perform(
            post("/api/family-invites/" + code + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dependentToken\":\"" + bruno.getToken() + "\"}")
                .with(as(maria.getUser().getToken())))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(STUDENTS).with(as(carlosUid)))
        .andExpect(jsonPath("$[0].token").value(joined))
        .andExpect(jsonPath("$[0].familyStatus").value("FAMILY_JOINED"))
        .andExpect(jsonPath("$[0].familyMatch.dependentName").value("Bruno Costa"))
        .andExpect(jsonPath("$[1].token").value(invited))
        .andExpect(jsonPath("$[1].familyStatus").value("INVITED"))
        .andExpect(jsonPath("$[1].invitedAt").isNotEmpty());

    mockMvc
        .perform(post(PASSENGERS + "/" + joined + "/merge").with(as(carlosUid)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(STUDENTS).with(as(carlosUid)))
        .andExpect(jsonPath("$[0].name").value("Bruno Costa"))
        .andExpect(jsonPath("$[0].familyStatus").value("WITH_FAMILY"));
  }

  @Test
  void filteringByVanKeepsThatVanAndTheStudentsWithoutAVan() throws Exception {
    VehicleModel vanA = fixtures.createVehicle(carlos, "AAA1A11");
    VehicleModel vanB = fixtures.createVehicle(carlos, "BBB2B22");
    createPassenger("Ana", vanA.getToken());
    createPassenger("Bia", vanB.getToken());
    createPassenger("Caio", null);

    mockMvc
        .perform(get(STUDENTS).param("vehicleToken", vanA.getToken()).with(as(carlosUid)))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Ana"))
        .andExpect(jsonPath("$[0].vehicleToken").value(vanA.getToken()))
        .andExpect(jsonPath("$[1].name").value("Caio"));
    mockMvc
        .perform(
            get("/api/drivers/me/occupancy")
                .param("vehicleToken", vanA.getToken())
                .with(as(carlosUid)))
        .andExpect(jsonPath("$.students").value(2));
    mockMvc.perform(get(STUDENTS).with(as(carlosUid))).andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void anotherDriversVanIsNotFound() throws Exception {
    DriverModel joana = fixtures.createDriver("Joana Lima", "joana@vanep.com", "11144477735");
    VehicleModel joanasVan = fixtures.createVehicle(joana, "JJJ3J33");

    mockMvc
        .perform(get(STUDENTS).param("vehicleToken", joanasVan.getToken()).with(as(carlosUid)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            postJson(
                    PASSENGERS,
                    fixtures.passengerRequest("Ana", school, city, joanasVan.getToken()))
                .with(as(carlosUid)))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void onlyADriverListsStudents() throws Exception {
    mockMvc.perform(get(STUDENTS)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(STUDENTS).with(as(maria.getUser().getToken())))
        .andExpect(status().isForbidden());
  }

  @Test
  void theListIssuesTheSameNumberOfStatementsWhateverTheNumberOfStudents() throws Exception {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    createActiveContract(bruno);
    String first = createPassenger("Ana", null);
    mockMvc.perform(post(PASSENGERS + "/" + first + "/invite").with(as(carlosUid)));
    long withFew = counter.countStatements(() -> service.findMyStudents(carlosUid, null));

    for (String name : List.of("Bia", "Caio", "Davi", "Eva", "Fabio")) {
      String passenger = createPassenger(name, null);
      mockMvc.perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(carlosUid)));
    }
    long withMany = counter.countStatements(() -> service.findMyStudents(carlosUid, null));

    assertThat(withMany).isEqualTo(withFew);
  }

  @Test
  void anActiveContractForTheMergedDependentDoesNotDuplicateTheStudent() throws Exception {
    String passenger = createPassenger("Bruno", null);
    String code = inviteCode(passenger);
    mockMvc
        .perform(
            post("/api/family-invites/" + code + "/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dependentToken\":\"" + bruno.getToken() + "\"}")
                .with(as(maria.getUser().getToken())))
        .andExpect(status().isOk());
    mockMvc
        .perform(post(PASSENGERS + "/" + passenger + "/merge").with(as(carlosUid)))
        .andExpect(status().isOk());

    createActiveContract(bruno);

    mockMvc
        .perform(get(STUDENTS).with(as(carlosUid)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].source").value("CONTRACT_ITEM"));
  }

  private void createActiveContract(DependentModel dependent) throws Exception {
    ClientDriverModel link =
        links
            .findByPair(maria.getId(), carlos.getId())
            .orElseGet(
                () -> {
                  ClientDriverModel created = new ClientDriverModel();
                  created.setClient(maria);
                  created.setDriver(carlos);
                  created.setOrigin(LinkOrigin.CLIENT_SEARCH);
                  created.setStatus(RelationshipStatus.PENDING);
                  return links.save(created);
                });
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("dependentToken", dependent.getToken());
    item.put(
        "slots",
        List.of(
            Map.of(
                "weekday",
                "MONDAY",
                "leg",
                "OUTBOUND",
                "shift",
                "MORNING",
                "pickupTime",
                "06:40")));
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientDriverToken", link.getToken());
    request.put("startsOn", LocalDate.now().minusMonths(1).toString());
    request.put("endsOn", LocalDate.now().plusMonths(6).toString());
    request.put("totalAmount", new BigDecimal("4200.00"));
    request.put("installments", 12);
    request.put("dueDay", 5);
    request.put("items", List.of(item));
    mockMvc
        .perform(postJson("/api/contracts", request).with(admin()))
        .andExpect(status().isCreated());
  }

  private String createPassenger(String name, String vehicleToken) throws Exception {
    String body =
        mockMvc
            .perform(
                postJson(PASSENGERS, fixtures.passengerRequest(name, school, city, vehicleToken))
                    .with(as(carlosUid)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  private String inviteCode(String passenger) throws Exception {
    String body =
        mockMvc
            .perform(post(PASSENGERS + "/" + passenger + "/invite").with(as(carlosUid)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode invite = objectMapper.readTree(body);
    return invite.get("code").asText();
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt().jwt(token -> token.claim("uid", uid).subject(uid));
  }

  private JwtRequestPostProcessor admin() {
    return jwt()
        .jwt(token -> token.claim("roles", List.of("ROLE_ADMIN")).subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("create_contract"));
  }
}
