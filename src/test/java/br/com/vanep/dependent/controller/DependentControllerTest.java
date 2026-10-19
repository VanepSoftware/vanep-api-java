package br.com.vanep.dependent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
  @Autowired private SchoolRepository schools;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private MessageSource messages;

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

  private SchoolModel persistSchool(String name) {
    SchoolModel school = new SchoolModel();
    school.setName(name);
    return schools.save(school);
  }

  @MockitoBean private PlacesClient places;

  private String catalogAddressJson(String number) {
    return catalogAddressJson(cityToken, "Rua do Embarque", "13015904", number);
  }

  private String catalogAddressJson(
      String cityTokenValue, String street, String zipCode, String number) {
    return "{\"cityToken\":\""
        + cityTokenValue
        + "\",\"street\":\""
        + street
        + "\",\"zipCode\":\""
        + zipCode
        + "\",\"number\":\""
        + number
        + "\"}";
  }

  private CityModel saveCity(String name, String uf) {
    CountryModel country = countries.findAll().getFirst();
    StateModel state = new StateModel();
    state.setName(name + " State");
    state.setUf(uf);
    state.setCountry(country);
    state = states.save(state);
    CityModel city = new CityModel();
    city.setState(state);
    city.setName(name);
    return cities.save(city);
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
        .andExpect(jsonPath("$.school").value(nullValue()))
        .andExpect(jsonPath("$.address").value(nullValue()))
        .andExpect(jsonPath("$.addressToken").doesNotExist());
  }

  @Test
  void createIgnoresAShiftSentAndTheResponseHasNone() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"shift\":\"FULLTIME\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shift").doesNotExist());
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
                    "{\"name\":\"Lucas Souza\",\"address\":" + catalogAddressJson("200") + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address.street").value("Rua do Embarque"))
        .andExpect(jsonPath("$.address.number").value("200"))
        .andExpect(jsonPath("$.address.zipCode").value("13015904"))
        .andExpect(jsonPath("$.address.cityToken").value(cityToken))
        .andExpect(jsonPath("$.address.token").isNotEmpty())
        .andExpect(jsonPath("$.addressToken").doesNotExist());

    DependentModel saved = dependents.findByClientId(ownerClientId).getFirst();
    assertThat(saved.getAddressId()).isNotNull();
    assertThat(saved.getAddressId()).isNotEqualTo(home.getId());
    AddressModel stored = addresses.findById(saved.getAddressId()).orElseThrow();
    assertThat(stored.getToken()).isNotEqualTo(home.getToken());
    assertThat(stored.getGooglePlaceId()).isNull();
  }

  @Test
  void createWithNeighborhoodPersistsItAndReturnsItWithoutCreatingADistrict() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":{\"cityToken\":\""
                        + cityToken
                        + "\",\"street\":\"Rua do Embarque\",\"zipCode\":\"13015904\","
                        + "\"neighborhood\":\"Bela Vista\"}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address.neighborhood").value("Bela Vista"))
        .andExpect(jsonPath("$.address.district").doesNotExist());

    DependentModel saved = dependents.findByClientId(ownerClientId).getFirst();
    assertThat(addresses.findById(saved.getAddressId()).orElseThrow().getNeighborhood())
        .isEqualTo("Bela Vista");
  }

  @Test
  void getReturnsThePickupNeighborhood() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    pickup.setNeighborhood("Centro");
    addresses.save(pickup);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.neighborhood").value("Centro"));
  }

  @Test
  void createAddressWithoutZipCodeReturns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":{\"cityToken\":\""
                        + cityToken
                        + "\",\"street\":\"Rua do Embarque\"}}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByClientId(ownerClientId)).isEmpty();
    assertThat(addresses.count()).isZero();
  }

  @Test
  void createAddressWithInvalidZipCodeReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":"
                        + catalogAddressJson(cityToken, "Rua do Embarque", "13015-904", "1")
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAddressWithBlankStreetReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":"
                        + catalogAddressJson(cityToken, "  ", "13015904", "1")
                        + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAddressWithUnknownCityTokenReturns404AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":"
                        + catalogAddressJson("doesnotexist", "Rua do Embarque", "13015904", "1")
                        + "}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("city.not_found"));

    assertThat(dependents.findByClientId(ownerClientId)).isEmpty();
    assertThat(addresses.count()).isZero();
  }

  @Test
  void createAddressIgnoresPlaceIdAndNeverCallsPlaces() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"address\":{\"placeId\":\"place-1\","
                        + "\"sessionToken\":\"session-1\",\"cityToken\":\""
                        + cityToken
                        + "\",\"street\":\"Rua do Embarque\",\"zipCode\":\"13015904\"}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.address.street").value("Rua do Embarque"));

    verifyNoInteractions(places);
  }

  @Test
  void createAddressWithOnlyAPlaceIdReturns400AndNeverCallsPlaces() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"address\":{\"placeId\":\"place-1\"}}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(places);
    assertThat(dependents.findByClientId(ownerClientId)).isEmpty();
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
  void createWithSchoolTokenLinksSchoolAndReturnsNested() throws Exception {
    SchoolModel school = persistSchool("Escola Teste");

    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lucas Souza\",\"schoolToken\":\"" + school.getToken() + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.school.token").value(school.getToken()))
        .andExpect(jsonPath("$.school.name").value("Escola Teste"));

    DependentModel saved = dependents.findByClientId(ownerClientId).getFirst();
    assertThat(saved.getSchoolId()).isEqualTo(school.getId());
  }

  @Test
  void createWithUnknownSchoolTokenReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"schoolToken\":\"missing-school\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createWithBlankSchoolTokenReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\",\"schoolToken\":\"\"}"))
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
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("dependent.not_found"));
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
  void patchNestedAddressReplacesTheOwnedRowInPlace() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":"
                        + catalogAddressJson(cityToken, "Avenida Nova", "13015000", "99")
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(pickup.getToken()))
        .andExpect(jsonPath("$.address.number").value("99"))
        .andExpect(jsonPath("$.address.street").value("Avenida Nova"))
        .andExpect(jsonPath("$.address.zipCode").value("13015000"));

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getAddressId())
        .isEqualTo(pickup.getId());
  }

  @Test
  void patchNestedAddressSwitchesTheCityKeepingTheSameRow() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    CityModel santos = saveCity("Santos", "RJ");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":"
                        + catalogAddressJson(santos.getToken(), "Rua do Porto", "11010000", "5")
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.token").value(pickup.getToken()))
        .andExpect(jsonPath("$.address.cityToken").value(santos.getToken()))
        .andExpect(jsonPath("$.address.cityName").value("Santos"));
  }

  @Test
  void patchAddressWithUnknownCityTokenReturns404WithCityCode() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":"
                        + catalogAddressJson("doesnotexist", "Rua do Embarque", "13015904", "1")
                        + "}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("city.not_found"));

    assertThat(addresses.findById(pickup.getId()).orElseThrow().getNumber()).isEqualTo("10");
  }

  @Test
  void patchUnknownDependentTokenReturns404WithDependentCode() throws Exception {
    mockMvc
        .perform(
            patch("/api/dependent/doesnotexist")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("dependent.not_found"));
  }

  @Test
  void patchNestedAddressNullsTheOptionalFieldsThatWereOmitted() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    pickup.setComplement("Bloco B");
    pickup.setNeighborhood("Centro");
    addresses.save(pickup);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":{\"cityToken\":\""
                        + cityToken
                        + "\",\"street\":\"Rua do Embarque\",\"zipCode\":\"13015904\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address.number").value(nullValue()))
        .andExpect(jsonPath("$.address.complement").value(nullValue()))
        .andExpect(jsonPath("$.address.neighborhood").value(nullValue()));

    AddressModel reloaded = addresses.findById(pickup.getId()).orElseThrow();
    assertThat(reloaded.getComplement()).isNull();
    assertThat(reloaded.getNeighborhood()).isNull();
  }

  @Test
  void patchPartialAddressReturns400AndKeepsTheStoredAddress() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":{\"number\":\"99\"}}"))
        .andExpect(status().isBadRequest());

    AddressModel reloaded = addresses.findById(pickup.getId()).orElseThrow();
    assertThat(reloaded.getNumber()).isEqualTo("10");
    assertThat(reloaded.getStreet()).isEqualTo("Rua do Embarque");
  }

  @Test
  void patchAddressAlsoOwnedByASchoolReturns409AndKeepsTheRow() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    AddressModel pickup = persistPickupAddress(own, "10");
    SchoolModel school = persistSchool("Escola Compartilhada");
    school.setAddressId(pickup.getId());
    schools.save(school);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":"
                        + catalogAddressJson(cityToken, "Avenida Nova", "13015000", "99")
                        + "}"))
        .andExpect(status().isConflict());

    assertThat(addresses.findById(pickup.getId()).orElseThrow().getStreet())
        .isEqualTo("Rua do Embarque");
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
  void patchSchoolTokenLinksSchool() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    SchoolModel school = persistSchool("Escola Teste");

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":\"" + school.getToken() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.school.token").value(school.getToken()))
        .andExpect(jsonPath("$.school.name").value("Escola Teste"));

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getSchoolId())
        .isEqualTo(school.getId());
  }

  @Test
  void patchSchoolTokenNullClearsSchool() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    SchoolModel school = persistSchool("Escola Teste");
    own.setSchoolId(school.getId());
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.school").value(nullValue()));

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getSchoolId()).isNull();
  }

  @Test
  void patchOmitSchoolTokenLeavesSchoolUnchanged() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    SchoolModel school = persistSchool("Escola Teste");
    own.setSchoolId(school.getId());
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.school.token").value(school.getToken()))
        .andExpect(jsonPath("$.school.name").value("Escola Teste"));

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getSchoolId())
        .isEqualTo(school.getId());
  }

  @Test
  void patchUnknownSchoolTokenReturns404() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":\"missing-school\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void patchBlankSchoolTokenReturns400() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolToken\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsNestedSchoolForLinkedDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    SchoolModel school = persistSchool("Escola Teste");
    own.setSchoolId(school.getId());
    dependents.save(own);

    mockMvc
        .perform(get("/api/dependent").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].school.token").value(school.getToken()))
        .andExpect(jsonPath("$[0].school.name").value("Escola Teste"));
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
  void patchReturns400WhenTheDocumentIsNotAValidCpfAndKeepsTheStoredOne() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setDocument("39053344705");
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"52998224724\"}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getDocument())
        .isEqualTo("39053344705");
  }

  @Test
  void createReturns400WhenTheDocumentIsNotAValidCpfAndStoresNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas\",\"document\":\"11111111111\"}"))
        .andExpect(status().isBadRequest());

    assertThat(dependents.findByClientId(ownerClientId)).isEmpty();
  }

  @Test
  void createStoresAMaskedCpfAsDigitsOnly() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas\",\"document\":\"529.982.247-25\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.document").value("52998224725"));
  }

  @Test
  void patchNullDocumentClearsTheDocument() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setDocument("39053344705");
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document").value(nullValue()));
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
    first.setDocument("52998224725");
    dependents.save(first);
    DependentModel second = createDependent(ownerClientId, "Second", false);
    second.setDocument("39053344705");
    dependents.save(second);

    mockMvc
        .perform(
            patch("/api/dependent/" + second.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"529.982.247-25\"}"))
        .andExpect(status().isConflict());

    assertThat(dependents.findByToken(second.getToken()).orElseThrow().getDocument())
        .isEqualTo("39053344705");
  }

  @Test
  void patchSameDocumentResentReturns200() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setDocument("52998224725");
    dependents.save(own);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":\"52998224725\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.document").value("52998224725"));
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
  void deletingLeavesNoLinkToTheRemovedPickupAddress() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    persistPickupAddress(own, "10");

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isOk());

    assertThat(dependents.findByToken(own.getToken()).orElseThrow().getAddressId()).isNull();
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

  @Test
  void replacingTheSchoolDaysReturnsThemMondayFirstAndGetReadsTheSame() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(
            putSchoolDays(
                    own,
                    schoolDaysBody(
                        day("FRIDAY", "MORNING"),
                        day("TUESDAY", "FULLTIME"),
                        day("MONDAY", "MORNING"),
                        day("THURSDAY", "FULLTIME"),
                        day("WEDNESDAY", "MORNING")))
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].weekday")
                .value(contains("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")))
        .andExpect(
            jsonPath("$[*].schoolShift")
                .value(contains("MORNING", "FULLTIME", "MORNING", "FULLTIME", "MORNING")));

    mockMvc
        .perform(get(schoolDaysPath(own)).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].weekday")
                .value(contains("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")))
        .andExpect(
            jsonPath("$[*].schoolShift")
                .value(contains("MORNING", "FULLTIME", "MORNING", "FULLTIME", "MORNING")));
  }

  @Test
  void aDependentWithoutSchoolDaysHasAnEmptyGrid() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get(schoolDaysPath(own)).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(empty()));
  }

  @Test
  void replacingAgainKeepsOnlyTheNewGrid() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"), day("TUESDAY", "MORNING")));

    replaceSchoolDays(own, schoolDaysBody(day("MONDAY", "AFTERNOON")));

    mockMvc
        .perform(get(schoolDaysPath(own)).with(ownerJwt()))
        .andExpect(jsonPath("$[*].weekday").value(contains("MONDAY")))
        .andExpect(jsonPath("$[*].schoolShift").value(contains("AFTERNOON")));
  }

  @Test
  void anEmptyListClearsTheSchoolDays() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING")));

    mockMvc
        .perform(putSchoolDays(own, schoolDaysBody()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(empty()));

    mockMvc
        .perform(get(schoolDaysPath(own)).with(ownerJwt()))
        .andExpect(jsonPath("$").value(empty()));
  }

  @Test
  void theSameWeekdayTwiceReturns400AndKeepsTheGrid() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("FRIDAY", "NIGHT")));

    mockMvc
        .perform(
            putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"), day("MONDAY", "FULLTIME")))
                .with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("dependent.school_days.weekday_duplicate")));

    expectStoredGrid(own, "FRIDAY", "NIGHT");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"days\":[{\"schoolShift\":\"MORNING\"}]}",
        "{\"days\":[{\"weekday\":\"MONDAY\"}]}",
        "{\"days\":[{\"weekday\":\"FUNDAY\",\"schoolShift\":\"MORNING\"}]}",
        "{\"days\":[{\"weekday\":\"MONDAY\",\"schoolShift\":\"EVENING\"}]}",
        "{\"days\":[null]}",
        "{\"days\":null}",
        "{}",
        "{\"days\":\"MONDAY\"}",
        "{\"days\":[{\"weekday\":\"MONDAY\",\"schoolShift\":true}]}",
        "{\"days\":["
      })
  void anInvalidSchoolDaysBodyReturns400AndKeepsTheGrid(String body) throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("FRIDAY", "NIGHT")));

    mockMvc.perform(putSchoolDays(own, body).with(ownerJwt())).andExpect(status().isBadRequest());

    expectStoredGrid(own, "FRIDAY", "NIGHT");
  }

  @Test
  void anotherClientCannotReadNorReplaceTheSchoolDays() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("FRIDAY", "NIGHT")));

    mockMvc.perform(get(schoolDaysPath(own)).with(otherJwt())).andExpect(status().isForbidden());
    mockMvc
        .perform(putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"))).with(otherJwt()))
        .andExpect(status().isForbidden());

    expectStoredGrid(own, "FRIDAY", "NIGHT");
  }

  @Test
  void theAdminReadsAndReplacesTheSchoolDaysOfAnyDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"))).with(adminJwt()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(schoolDaysPath(own)).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].weekday").value(contains("MONDAY")));
  }

  @Test
  void theSchoolDaysRequireAuthentication() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc.perform(get(schoolDaysPath(own))).andExpect(status().isUnauthorized());
    mockMvc
        .perform(putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void readingTheSchoolDaysRequiresShowDependentAndReplacingRequiresUpdateDependent()
      throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    JwtRequestPostProcessor withoutDependentPermissions =
        jwt()
            .jwt(token -> token.subject(OWNER_EMAIL).claim("roles", List.of("ROLE_CLIENT")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));

    mockMvc
        .perform(get(schoolDaysPath(own)).with(withoutDependentPermissions))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING")))
                .with(withoutDependentPermissions))
        .andExpect(status().isForbidden());
  }

  @Test
  void theSchoolDaysOfAnUnknownDependentAreNotFound() throws Exception {
    mockMvc
        .perform(get("/api/dependent/doesnotexist/school-days").with(ownerJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("dependent.not_found"));
    mockMvc
        .perform(
            put("/api/dependent/doesnotexist/school-days")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(schoolDaysBody(day("MONDAY", "MORNING"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void theSchoolDaysOfADeletedDependentAreNotFound() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    dependents.delete(own);

    mockMvc.perform(get(schoolDaysPath(own)).with(ownerJwt())).andExpect(status().isNotFound());
    mockMvc
        .perform(putSchoolDays(own, schoolDaysBody(day("MONDAY", "MORNING"))).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void replacingTheSchoolDaysKeepsTheOtherFields() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    own.setBirthDate(LocalDate.of(2016, 4, 2));
    own.setSchoolId(persistSchool("Escola Azul").getId());
    dependents.save(own);
    persistPickupAddress(own, "42");

    replaceSchoolDays(own, schoolDaysBody(day("MONDAY", "FULLTIME")));

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(jsonPath("$.name").value("Own Kid"))
        .andExpect(jsonPath("$.birthDate").value("2016-04-02"))
        .andExpect(jsonPath("$.school.name").value("Escola Azul"))
        .andExpect(jsonPath("$.address.number").value("42"));
  }

  @Test
  void aDeletedAndRestoredDependentKeepsItsSchoolDays() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    replaceSchoolDays(own, schoolDaysBody(day("TUESDAY", "FULLTIME")));

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isOk());

    expectStoredGrid(own, "TUESDAY", "FULLTIME");
  }

  private String schoolDaysPath(DependentModel dependent) {
    return "/api/dependent/" + dependent.getToken() + "/school-days";
  }

  private MockHttpServletRequestBuilder putSchoolDays(DependentModel dependent, String body) {
    return put(schoolDaysPath(dependent)).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void replaceSchoolDays(DependentModel dependent, String body) throws Exception {
    mockMvc.perform(putSchoolDays(dependent, body).with(ownerJwt())).andExpect(status().isOk());
  }

  private void expectStoredGrid(DependentModel dependent, String weekday, String schoolShift)
      throws Exception {
    mockMvc
        .perform(get(schoolDaysPath(dependent)).with(ownerJwt()))
        .andExpect(jsonPath("$[*].weekday").value(contains(weekday)))
        .andExpect(jsonPath("$[*].schoolShift").value(contains(schoolShift)));
  }

  private String day(String weekday, String schoolShift) {
    return "{\"weekday\":\"" + weekday + "\",\"schoolShift\":\"" + schoolShift + "\"}";
  }

  private String schoolDaysBody(String... days) {
    return "{\"days\":[" + String.join(",", days) + "]}";
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
