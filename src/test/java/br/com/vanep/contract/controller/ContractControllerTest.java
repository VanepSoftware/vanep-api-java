package br.com.vanep.contract.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class ContractControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private MessageSource messages;
  @Autowired private ContractRepository contracts;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private DependentRepository dependents;
  @Autowired private AddressRepository addresses;
  @Autowired private SchoolRepository schools;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;
  private ClientDriverModel link;
  private DependentModel lucas;
  private String mariaUid;
  private String carlosUid;
  private String strangerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CityModel city = createCity();
    ClientModel maria = createClient("maria@vanep.com", "11144477735");
    DriverModel carlos = createDriver("carlos@vanep.com", "52998224725");
    mariaUid = maria.getUser().getToken();
    carlosUid = carlos.getUser().getToken();
    strangerUid = createClient("bruno@vanep.com", "12345678909").getUser().getToken();

    link = createLink(maria, carlos);
    lucas = createDependent(maria, createSchool(city), createAddress(city));
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/contracts")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/contracts/anything")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/client-drivers/" + link.getToken() + "/contracts"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCreatesAnActiveContract() throws Exception {
    mockMvc
        .perform(postContract(createRequest(), adminJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.clientDriverToken").value(link.getToken()))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
        .andExpect(jsonPath("$.items[0].pickupStreet").value("Rua A"))
        .andExpect(jsonPath("$.items[0].slots[0].leg").value("OUTBOUND"));
  }

  @Test
  void aClientCannotCreateAContract() throws Exception {
    mockMvc
        .perform(postContract(createRequest(), partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isForbidden());

    assertThat(contracts.count()).isZero();
  }

  @Test
  void bothPartiesReadTheContract() throws Exception {
    String token = createContract();

    mockMvc
        .perform(get("/api/contracts/" + token).with(partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(token));
    mockMvc
        .perform(get("/api/contracts/" + token).with(partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(token));
  }

  @Test
  void anotherClientCannotReadTheContract() throws Exception {
    String token = createContract();

    mockMvc
        .perform(get("/api/contracts/" + token).with(partyJwt(strangerUid, "CLIENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void bothPartiesListTheContractsOfTheirLink() throws Exception {
    String token = createContract();

    mockMvc
        .perform(linkContracts().with(partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(token));
    mockMvc
        .perform(linkContracts().with(partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void anotherClientCannotListTheContractsOfTheLink() throws Exception {
    createContract();

    mockMvc
        .perform(linkContracts().with(partyJwt(strangerUid, "CLIENT")))
        .andExpect(status().isForbidden());
  }

  @Test
  void neitherPartyCanPatchTheContract() throws Exception {
    String token = createContract();

    mockMvc
        .perform(patchContract(token, "{\"status\":\"ENDED\"}", partyJwt(carlosUid, "DRIVER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(patchContract(token, "{\"status\":\"ENDED\"}", partyJwt(mariaUid, "CLIENT")))
        .andExpect(status().isForbidden());

    assertThat(contracts.findByToken(token).orElseThrow().getStatus())
        .isEqualTo(ContractStatus.ACTIVE);
  }

  @Test
  void adminEndsAnActiveContract() throws Exception {
    String token = createContract();

    mockMvc
        .perform(patchContract(token, "{\"status\":\"ENDED\"}", adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENDED"));
  }

  @Test
  void clearingTheStatusIsRefused() throws Exception {
    String token = createContract();

    mockMvc
        .perform(patchContract(token, "{\"status\":null}", adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anEmptyPatchLeavesEveryStoredFieldUnchanged() throws Exception {
    String token = createContract();
    ContractModel before = contracts.findByToken(token).orElseThrow();

    mockMvc
        .perform(patchContract(token, "{}", adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    ContractModel after = contracts.findByToken(token).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(before.getStatus());
    assertThat(after.getStartsOn()).isEqualTo(before.getStartsOn());
    assertThat(after.getEndsOn()).isEqualTo(before.getEndsOn());
    assertThat(after.getTotalAmount()).isEqualByComparingTo(before.getTotalAmount());
    assertThat(after.getInstallments()).isEqualTo(before.getInstallments());
    assertThat(after.getDueDay()).isEqualTo(before.getDueDay());
    assertThat(after.getClientDriver().getToken()).isEqualTo(link.getToken());
    assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
  }

  @Test
  void adminDeletesTheContractAndItLeavesTheListing() throws Exception {
    String token = createContract();

    mockMvc
        .perform(delete("/api/contracts/" + token).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/contracts").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void adminRestoresADeletedContract() throws Exception {
    String token = createContract();
    mockMvc
        .perform(delete("/api/contracts/" + token).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/contracts/" + token + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(token))
        .andExpect(jsonPath("$.items.length()").value(1));

    mockMvc
        .perform(get("/api/contracts").with(adminJwt()))
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  void rejectsACreateWithoutEndDate() throws Exception {
    Map<String, Object> request = createRequest();
    request.remove("endsOn");

    mockMvc.perform(postContract(request, adminJwt())).andExpect(status().isBadRequest());

    assertThat(contracts.count()).isZero();
  }

  @Test
  void rejectsASlotWithoutShift() throws Exception {
    Map<String, Object> slot = mondayOutboundSlot();
    slot.remove("shift");

    mockMvc
        .perform(postContract(createRequest(slot), adminJwt()))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({
    "dueDay, 31, contract.due_day.invalid",
    "installments, 13, contract.installments.invalid",
    "totalAmount, 0, contract.amount.invalid"
  })
  void rejectsAnOutOfRangeTermWithTheMessageOfItsKey(String field, int value, String key)
      throws Exception {
    Map<String, Object> request = createRequest();
    request.put(field, value);

    mockMvc
        .perform(postContract(request, adminJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(fieldErrorMessage(field, message(key)));
  }

  @Test
  void rejectsAnEndDateOnTheStartDate() throws Exception {
    Map<String, Object> request = createRequest();
    request.put("endsOn", request.get("startsOn"));

    mockMvc
        .perform(postContract(request, adminJwt()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(message("contract.period.invalid")));
  }

  @Test
  void responsesExposeTokensAndNoNumericIdentifier() throws Exception {
    String token = createContract();

    String single =
        mockMvc
            .perform(get("/api/contracts/" + token).with(partyJwt(mariaUid, "CLIENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(token))
            .andExpect(jsonPath("$.clientDriverToken").value(link.getToken()))
            .andExpect(jsonPath("$.items[0].token").isNotEmpty())
            .andExpect(jsonPath("$.items[0].dependentToken").value(lucas.getToken()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String listing =
        mockMvc
            .perform(get("/api/contracts").with(adminJwt()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(List.of(single, listing))
        .allSatisfy(body -> assertThat(body).doesNotContainPattern("\"(id|[A-Za-z]+Id)\"\\s*:"));
  }

  private String createContract() throws Exception {
    String body =
        mockMvc
            .perform(postContract(createRequest(), adminJwt()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.token");
  }

  private MockHttpServletRequestBuilder postContract(
      Map<String, Object> request, JwtRequestPostProcessor caller) throws Exception {
    return post("/api/contracts")
        .with(caller)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request));
  }

  private MockHttpServletRequestBuilder patchContract(
      String token, String body, JwtRequestPostProcessor caller) {
    return patch("/api/contracts/" + token)
        .with(caller)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private MockHttpServletRequestBuilder linkContracts() {
    return get("/api/client-drivers/" + link.getToken() + "/contracts");
  }

  private Map<String, Object> createRequest() {
    return createRequest(mondayOutboundSlot());
  }

  private Map<String, Object> createRequest(Map<String, Object> slot) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("dependentToken", lucas.getToken());
    item.put("monthlyAmount", new BigDecimal("350.00"));
    item.put("slots", List.of(slot));

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("clientDriverToken", link.getToken());
    request.put("startsOn", "2027-02-01");
    request.put("endsOn", "2027-12-15");
    request.put("totalAmount", new BigDecimal("4200.00"));
    request.put("installments", 12);
    request.put("dueDay", 5);
    request.put("items", List.of(item));
    return request;
  }

  private Map<String, Object> mondayOutboundSlot() {
    Map<String, Object> slot = new LinkedHashMap<>();
    slot.put("weekday", "MONDAY");
    slot.put("leg", "OUTBOUND");
    slot.put("shift", "MORNING");
    slot.put("windowStart", "06:40");
    return slot;
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

  private ClientDriverModel createLink(ClientModel client, DriverModel driver) {
    ClientDriverModel model = new ClientDriverModel();
    model.setClient(client);
    model.setDriver(driver);
    model.setStatus(RelationshipStatus.PENDING);
    return links.save(model);
  }

  private DependentModel createDependent(
      ClientModel client, SchoolModel school, AddressModel address) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName("Lucas");
    dependent.setSchoolId(school.getId());
    dependent.setAddressId(address.getId());
    return dependents.save(dependent);
  }

  private AddressModel createAddress(CityModel city) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet("Rua A");
    address.setNumber("10");
    return addresses.save(address);
  }

  private SchoolModel createSchool(CityModel city) {
    SchoolModel school = new SchoolModel();
    school.setName("Escola Municipal");
    school.setCity(city);
    return schools.save(school);
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

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    return cities.save(city);
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

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(token -> token.claim("roles", List.of("ROLE_ADMIN")).subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_contracts"),
            new SimpleGrantedAuthority("show_contract"),
            new SimpleGrantedAuthority("create_contract"),
            new SimpleGrantedAuthority("update_contract"),
            new SimpleGrantedAuthority("delete_contract"),
            new SimpleGrantedAuthority("restore_contract"));
  }

  private JwtRequestPostProcessor partyJwt(String uid, String role) {
    return jwt()
        .jwt(token -> token.claim("uid", uid).claim("roles", List.of("ROLE_" + role)).subject(uid))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
