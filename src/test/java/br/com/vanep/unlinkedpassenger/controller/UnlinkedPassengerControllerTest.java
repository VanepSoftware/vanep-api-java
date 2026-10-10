package br.com.vanep.unlinkedpassenger.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UnlinkedPassengerControllerTest {

  private static final String BASE = "/api/drivers/me/unlinked-passengers";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private SchoolRepository schools;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;
  private CityModel city;
  private SchoolModel school;
  private String carlosUid;
  private String joanaUid;
  private String mariaUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    city = createCity();
    school = createSchool("Escola Municipal");
    carlosUid = createDriver("carlos@vanep.com", "52998224725", DriverApprovalStatus.APPROVED);
    joanaUid = createDriver("joana@vanep.com", "11144477735", DriverApprovalStatus.PENDING);
    mariaUid = createUser(UserType.CLIENT, "Maria", "maria@vanep.com", "12345678909").getToken();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    mockMvc.perform(postJson(BASE, createRequest("Lucas"))).andExpect(status().isUnauthorized());
  }

  @Test
  void aClientCannotManageUnlinkedPassengers() throws Exception {
    mockMvc.perform(get(BASE).with(as(mariaUid))).andExpect(status().isForbidden());
    mockMvc
        .perform(postJson(BASE, createRequest("Lucas")).with(as(mariaUid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void aDriverRegistersAStudentTheyAlreadyTransport() throws Exception {
    mockMvc
        .perform(postJson(BASE, createRequest("Lucas")).with(as(carlosUid)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Lucas"))
        .andExpect(jsonPath("$.schoolToken").value(school.getToken()))
        .andExpect(jsonPath("$.schoolShift").value("MORNING"))
        .andExpect(jsonPath("$.address.street").value("Rua das Flores"))
        .andExpect(jsonPath("$.address.cityToken").value(city.getToken()))
        .andExpect(jsonPath("$.slots.length()").value(2))
        .andExpect(jsonPath("$.slots[0].leg").value("OUTBOUND"));
  }

  @Test
  void aDriverStillInReviewCanRegisterPassengers() throws Exception {
    mockMvc
        .perform(postJson(BASE, createRequest("Lucas")).with(as(joanaUid)))
        .andExpect(status().isCreated());
  }

  @Test
  void eachDriverListsOnlyTheirOwnPassengers() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");
    createPassenger(joanaUid, "Ana");

    mockMvc
        .perform(get(BASE).with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(lucas));
  }

  @Test
  void anotherDriversPassengerIsNotFound() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");

    mockMvc
        .perform(get(BASE + "/" + lucas).with(as(joanaUid)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("unlinked_passenger.not_found")));
    mockMvc
        .perform(patchJson(BASE + "/" + lucas, Map.of("notes", "x")).with(as(joanaUid)))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete(BASE + "/" + lucas).with(as(joanaUid))).andExpect(status().isNotFound());
    mockMvc
        .perform(get(BASE + "/" + lucas).with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").doesNotExist());
  }

  @Test
  void aNotesOnlyPatchKeepsEveryOtherField() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");
    JsonNode before = readPassenger(lucas);

    mockMvc
        .perform(patchJson(BASE + "/" + lucas, Map.of("notes", "Portão azul")).with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("Portão azul"));

    JsonNode after = readPassenger(lucas);
    assertThat(after.get("name")).isEqualTo(before.get("name"));
    assertThat(after.get("schoolToken")).isEqualTo(before.get("schoolToken"));
    assertThat(after.get("schoolShift")).isEqualTo(before.get("schoolShift"));
    assertThat(after.get("address")).isEqualTo(before.get("address"));
    assertThat(after.get("slots")).isEqualTo(before.get("slots"));
  }

  @Test
  void aPatchChangesOnlyTheFieldsSent() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");
    SchoolModel otherSchool = createSchool("Colégio Estadual");

    mockMvc
        .perform(
            patchJson(
                    BASE + "/" + lucas,
                    Map.of(
                        "name",
                        "Lucas Silva",
                        "schoolToken",
                        otherSchool.getToken(),
                        "schoolShift",
                        "AFTERNOON",
                        "address",
                        addressRequest("Avenida Nova")))
                .with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Lucas Silva"))
        .andExpect(jsonPath("$.schoolToken").value(otherSchool.getToken()))
        .andExpect(jsonPath("$.schoolShift").value("AFTERNOON"))
        .andExpect(jsonPath("$.address.street").value("Avenida Nova"));
  }

  @Test
  void aPatchWithANullNameIsRejected() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");
    Map<String, Object> body = new HashMap<>();
    body.put("name", null);

    mockMvc
        .perform(patchJson(BASE + "/" + lucas, body).with(as(carlosUid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("unlinked_passenger.name.required")));
    assertThat(readPassenger(lucas).get("name").asText()).isEqualTo("Lucas");
  }

  @Test
  void replacingTheScheduleReturnsTheNewSlots() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");

    mockMvc
        .perform(
            putJson(
                    BASE + "/" + lucas + "/schedule",
                    Map.of("slots", List.of(slot("FRIDAY", "RETURN", "AFTERNOON", "12:10"))))
                .with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots.length()").value(1))
        .andExpect(jsonPath("$.slots[0].weekday").value("FRIDAY"))
        .andExpect(jsonPath("$.slots[0].leg").value("RETURN"));
    assertThat(readPassenger(lucas).get("slots")).hasSize(1);
  }

  @Test
  void anEmptyScheduleIsRejected() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");

    mockMvc
        .perform(
            putJson(BASE + "/" + lucas + "/schedule", Map.of("slots", List.of()))
                .with(as(carlosUid)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("schedule.slots.required")));
  }

  @Test
  void deletingHidesThePassengerAndKeepsTheRow() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");

    mockMvc
        .perform(delete(BASE + "/" + lucas).with(as(carlosUid)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get(BASE).with(as(carlosUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc.perform(get(BASE + "/" + lucas).with(as(carlosUid))).andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from unlinked_passenger where token = ? and deleted_at is not null",
                Integer.class,
                lucas))
        .isEqualTo(1);
  }

  @Test
  void unknownPersonalFieldsAreNotStored() throws Exception {
    Map<String, Object> request = createRequest("Lucas");
    request.put("birthDate", "2018-03-10");
    request.put("document", "12345678909");

    String body =
        mockMvc
            .perform(postJson(BASE, request).with(as(carlosUid)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("birthDate").doesNotContain("12345678909");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.columns"
                    + " where lower(table_name) = 'unlinked_passenger'"
                    + " and lower(column_name) in ('birth_date', 'document')",
                Integer.class))
        .isZero();
  }

  @Test
  void aCreateWithoutANameIsRejected() throws Exception {
    Map<String, Object> request = createRequest("");

    mockMvc
        .perform(postJson(BASE, request).with(as(carlosUid)))
        .andExpect(status().isBadRequest())
        .andExpect(fieldErrorMessage("name", message("unlinked_passenger.name.required")));
  }

  @Test
  void responsesNeverExposeInternalIds() throws Exception {
    String lucas = createPassenger(carlosUid, "Lucas");

    String single =
        mockMvc
            .perform(get(BASE + "/" + lucas).with(as(carlosUid)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String listing =
        mockMvc
            .perform(get(BASE).with(as(carlosUid)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(List.of(single, listing))
        .allSatisfy(body -> assertThat(body).doesNotContainPattern("\"(id|[A-Za-z]+Id)\"\\s*:"));
  }

  private String createPassenger(String uid, String name) throws Exception {
    String body =
        mockMvc
            .perform(postJson(BASE, createRequest(name)).with(as(uid)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  private JsonNode readPassenger(String token) throws Exception {
    String body =
        mockMvc
            .perform(get(BASE + "/" + token).with(as(carlosUid)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  private Map<String, Object> createRequest(String name) {
    Map<String, Object> request = new HashMap<>();
    request.put("name", name);
    request.put("schoolToken", school.getToken());
    request.put("schoolShift", "MORNING");
    request.put("address", addressRequest("Rua das Flores"));
    request.put(
        "slots",
        List.of(
            slot("MONDAY", "RETURN", "AFTERNOON", "12:10"),
            slot("MONDAY", "OUTBOUND", "MORNING", "06:40")));
    return request;
  }

  private Map<String, Object> addressRequest(String street) {
    return Map.of(
        "cityToken", city.getToken(),
        "street", street,
        "zipCode", "13015904",
        "number", "100",
        "neighborhood", "Centro");
  }

  private Map<String, Object> slot(String weekday, String leg, String shift, String windowStart) {
    return Map.of("weekday", weekday, "leg", leg, "shift", shift, "windowStart", windowStart);
  }

  private MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
  }

  private MockHttpServletRequestBuilder patchJson(String path, Object body) throws Exception {
    return patch(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
  }

  private MockHttpServletRequestBuilder putJson(String path, Object body) throws Exception {
    return put(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  private ResultMatcher fieldErrorMessage(String field, String expected) {
    return result -> {
      MethodArgumentNotValidException exception =
          (MethodArgumentNotValidException) result.getResolvedException();
      assertThat(exception.getBindingResult().getFieldError(field).getDefaultMessage())
          .isEqualTo(expected);
    };
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt().jwt(token -> token.claim("uid", uid).subject(uid));
  }

  private SchoolModel createSchool(String name) {
    SchoolModel model = new SchoolModel();
    model.setName(name);
    model.setCity(city);
    return schools.save(model);
  }

  private CityModel createCity() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel model = new CityModel();
    model.setState(state);
    model.setName("Campinas");
    return cities.save(model);
  }

  private String createDriver(String email, String document, DriverApprovalStatus approval) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Motorista", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(approval);
    return drivers.save(driver).getUser().getToken();
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
}
