package br.com.vanep.dependent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.Gender;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
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
class DependentControllerTest {
  private static final String OWNER_EMAIL = "owner@vanep.com";
  private static final String OTHER_EMAIL = "other@vanep.com";
  private static final String ADMIN_EMAIL = "admin@vanep.com";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DependentRepository dependents;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private MockMvc mockMvc;

  private Long ownerClientId;
  private String ownerClientToken;
  private Long otherClientId;
  private String cityToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

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
    city = cities.save(city);
    cityToken = city.getToken();

    ClientModel owner = createClient(OWNER_EMAIL, "Owner Client", "10000000001");
    ownerClientId = owner.getId();
    ownerClientToken = owner.getToken();

    ClientModel other = createClient(OTHER_EMAIL, "Other Client", "20000000002");
    otherClientId = other.getId();
  }

  private ClientModel createClient(String email, String name, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ClientModel client = new ClientModel();
    client.setUser(user);
    return clients.save(client);
  }

  private DependentModel createDependent(Long clientId, String name, boolean isDefault) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(clientId);
    dependent.setName(name);
    dependent.setShift(Shift.MORNING);
    dependent.setDefaultDependent(isDefault);
    return dependents.save(dependent);
  }

  private AddressModel persistAddress(String street, String number) {
    CityModel city = cities.findByToken(cityToken).orElseThrow();
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet(street);
    address.setNumber(number);
    return addresses.save(address);
  }

  private AddressModel persistClientHomeAddress() {
    AddressModel address = persistAddress("Rua da Casa", "10");
    ClientModel client = clients.findByToken(ownerClientToken).orElseThrow();
    client.getUser().setAddressId(address.getId());
    users.save(client.getUser());
    return address;
  }

  private AddressModel persistPickupAddress(DependentModel dependent, String number) {
    AddressModel address = persistAddress("Rua do Embarque", number);
    dependent.setAddressId(address.getId());
    dependents.save(dependent);
    return address;
  }

  private String addressJson(String street, String number) {
    return "{\"cityToken\":\""
        + cityToken
        + "\",\"zipCode\":\"13015904\",\"street\":\""
        + street
        + "\",\"number\":\""
        + number
        + "\",\"district\":\"Centro\"}";
  }

  private JwtRequestPostProcessor jwtFor(String email, String role) {
    return jwt()
        .jwt(token -> token.subject(email).claim("roles", List.of(role)))
        .authorities(
            new SimpleGrantedAuthority(role),
            new SimpleGrantedAuthority("create_dependent"),
            new SimpleGrantedAuthority("list_dependents"),
            new SimpleGrantedAuthority("show_dependent"),
            new SimpleGrantedAuthority("update_dependent"),
            new SimpleGrantedAuthority("delete_dependent"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwtFor(OWNER_EMAIL, "ROLE_CLIENT");
  }

  private JwtRequestPostProcessor otherJwt() {
    return jwtFor(OTHER_EMAIL, "ROLE_CLIENT");
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwtFor(ADMIN_EMAIL, "ROLE_ADMIN");
  }

  @Test
  void createReturns201ForClient() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Lucas Souza"))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.client.token").value(ownerClientToken))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns400WhenNameMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenNameTooLong() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenEmailInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createWithNestedAddressPersistsExclusiveRowDistinctFromClientHome() throws Exception {
    AddressModel home = persistClientHomeAddress();

    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":"
                        + addressJson("Rua do Embarque", "200")
                        + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address.street").value("Rua do Embarque"))
        .andExpect(jsonPath("$.address.number").value("200"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken))
        .andExpect(jsonPath("$.address.token").isNotEmpty())
        .andExpect(jsonPath("$.addressToken").doesNotExist());

    DependentModel saved = dependents.findByClientId(ownerClientId).getFirst();
    assertThat(saved.getAddressId()).isNotNull();
    assertThat(saved.getAddressId()).isNotEqualTo(home.getId());
    assertThat(addresses.findById(saved.getAddressId()).orElseThrow().getToken())
        .isNotEqualTo(home.getToken());
  }

  @Test
  void createIgnoresAddressTokenAndDoesNotLinkCatalog() throws Exception {
    AddressModel catalog = persistAddress("Avenida Paulista", "1000");

    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"addressToken\":\"" + catalog.getToken() + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());

    DependentModel saved = dependents.findByClientId(ownerClientId).getFirst();
    assertThat(saved.getAddressId()).isNull();
  }

  @Test
  void createWithSchoolTokenReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"schoolToken\":\"school-tok\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsOnlyOwnDependentsForClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    createDependent(otherClientId, "Other Kid", true);

    mockMvc
        .perform(get("/api/dependent").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(own.getToken()))
        .andExpect(jsonPath("$[0].addressToken").doesNotExist());
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }

  @Test
  void getByTokenReturns403ForOtherClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(otherJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/dependent/doesnotexist").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Old Name", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void updateReturns403ForOtherClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Old Name", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(otherJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchNameOnlyLeavesPhoneEmailBirthDateAndAddressUnchanged() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Nome Antigo", true);
    own.setPhone("11988887777");
    own.setEmail("kid@vanep.com");
    own.setBirthDate(LocalDate.of(2015, 3, 20));
    own.setGender(Gender.MALE);
    dependents.save(own);
    AddressModel pickup = persistPickupAddress(own, "1481");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Novo"))
        .andExpect(jsonPath("$.phone").value("11988887777"))
        .andExpect(jsonPath("$.email").value("kid@vanep.com"))
        .andExpect(jsonPath("$.birthDate").value("2015-03-20"))
        .andExpect(jsonPath("$.address.token").value(pickup.getToken()))
        .andExpect(jsonPath("$.address.number").value("1481"))
        .andExpect(jsonPath("$.addressToken").doesNotExist());

    DependentModel reloaded = dependents.findByToken(own.getToken()).orElseThrow();
    assertThat(reloaded.getPhone()).isEqualTo("11988887777");
    assertThat(reloaded.getEmail()).isEqualTo("kid@vanep.com");
    assertThat(reloaded.getBirthDate()).isEqualTo(LocalDate.of(2015, 3, 20));
    assertThat(reloaded.getAddressId()).isEqualTo(pickup.getId());
  }

  @Test
  void patchOmitAddressKeepsPickupAddress() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(pickup.getToken()));
  }

  @Test
  void patchNestedAddressUpdatesOwnedRowInPlace() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + addressJson("Rua do Embarque", "99") + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(pickup.getToken()))
        .andExpect(jsonPath("$.address.number").value("99"));
  }

  @Test
  void patchNullAddressClearsPickupAddress() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    String pickupToken = pickup.getToken();

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()));

    DependentModel reloaded = dependents.findByToken(own.getToken()).orElseThrow();
    assertThat(reloaded.getAddressId()).isNull();
    assertThat(addresses.findByToken(pickupToken)).isEmpty();
  }

  @Test
  void patchNullPhoneClearsPhone() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setPhone("11988887777");
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value(nullValue()));

    DependentModel reloaded = dependents.findByToken(own.getToken()).orElseThrow();
    assertThat(reloaded.getPhone()).isNull();
  }

  @Test
  void patchSchoolTokenValueReturns400() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":\"school-tok\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchSchoolTokenNullReturns400() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchPresentBlankNameReturns400AndLeavesStoredName() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getName()).isEqualTo("Own Kid");
  }

  @Test
  void patchReturns400WhenNameTooLong() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "a".repeat(256) + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getName()).isEqualTo("Own Kid");
  }

  @Test
  void patchReturns400WhenPhoneTooLong() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + "1".repeat(33) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchReturns400WhenDocumentTooLong() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"" + "1".repeat(65) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchReturns400WhenEmailInvalid() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchPresentNullIsDefaultReturns400() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isDefault\":null}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().isDefaultDependent()).isTrue();
  }

  @Test
  void patchDuplicateDocumentOnAnotherDependentReturns409() throws Exception {
    DependentModel first = createDependent(ownerClientId, "First", true);
    first.setDocument("11111111111");
    dependents.save(first);
    DependentModel second = createDependent(ownerClientId, "Second", false);
    second.setDocument("22222222222");
    dependents.save(second);

    mockMvc
        .perform(
            patch("/api/dependent/" + second.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"11111111111\"}"))
        .andExpect(status().isConflict());

    assertThat(dependents.findByToken(second.getToken()).orElseThrow().getDocument())
        .isEqualTo("22222222222");
  }

  @Test
  void patchSameDocumentResentReturns200() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setDocument("11111111111");
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"11111111111\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document").value("11111111111"));
  }

  @Test
  void deleteReturns204AndThenGetReturns404() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteClearsPickupAddress() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    String pickupToken = pickup.getToken();

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    assertThat(addresses.findByToken(pickupToken)).isEmpty();
  }

  @Test
  void restoreReturns200AfterDeleteAndThenGetReturns200() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }

  @Test
  void restoreDependentHasAddressNull() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    persistPickupAddress(own, "10");

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void restoreReturns409ForActiveDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void adminListsAllDependents() throws Exception {
    createDependent(ownerClientId, "Own Kid", true);
    createDependent(otherClientId, "Other Kid", true);

    mockMvc
        .perform(get("/api/dependent").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void adminAccessesAnyDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }
}
