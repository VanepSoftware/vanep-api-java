package br.com.vanep.address.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.state.seed.StateSeeder;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PersonalAddressControllerTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String STREET = "QNL 5 Conjunto I";
  private static final String ZIP = "72115105";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private AddressRepository addresses;
  @Autowired private CountryRepository countries;
  @Autowired private StateRepository states;
  @Autowired private CityRepository cities;
  @Autowired private StateSeeder stateSeeder;
  @Autowired private DistrictRepository districts;
  @Autowired private MessageSource messages;

  @MockitoBean private PlacesClient places;

  private MockMvc mockMvc;
  private String callerUid;
  private String brasiliaToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    countries.save(brasil);

    stateSeeder.seed();
    brasiliaToken = seedIbgeCity("DF", "Brasília", "5300108").getToken();

    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista Sem Endereço");
    user.setEmail("driver@vanep.com");
    user.setDocument("98765432100");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    callerUid = users.save(user).getToken();
  }

  private JwtRequestPostProcessor caller() {
    return jwt().jwt(builder -> builder.claim("uid", callerUid).subject(callerUid));
  }

  private CityModel seedIbgeCity(String uf, String name, String ibgeCode) {
    CityModel city = new CityModel();
    city.setState(states.findByUf(uf).orElseThrow());
    city.setName(name);
    city.setIbgeCode(ibgeCode);
    return cities.save(city);
  }

  private Map<String, Object> validPostal() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("cityToken", brasiliaToken);
    body.put("street", STREET);
    body.put("zipCode", ZIP);
    return body;
  }

  private String json(Map<String, Object> body) throws Exception {
    return MAPPER.writeValueAsString(body);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Test
  void rejectsUnauthenticatedWrite() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsUnauthenticatedRead() throws Exception {
    mockMvc.perform(get("/api/user/me/address")).andExpect(status().isUnauthorized());
  }

  @Test
  void createsAddressFromCatalogCityStreetAndZip() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityToken").value(brasiliaToken))
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.stateUf").value("DF"))
        .andExpect(jsonPath("$.countryIsoCode").value("BR"))
        .andExpect(jsonPath("$.street").value(STREET))
        .andExpect(jsonPath("$.zipCode").value(ZIP))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.cityId").doesNotExist())
        .andExpect(jsonPath("$.googlePlaceId").doesNotExist());

    AddressModel saved = addresses.findAll().getFirst();
    assertThat(saved.getDistrict()).isNull();
    assertThat(saved.getGooglePlaceId()).isNull();
    assertThat(districts.count()).isZero();
    assertThat(users.findByToken(callerUid).orElseThrow().getAddressId()).isEqualTo(saved.getId());
    verify(places, never()).findPlaceDetails(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  void rejectsOmittedZipCode() throws Exception {
    Map<String, Object> body = validPostal();
    body.remove("zipCode");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isBadRequest());

    assertThat(addresses.count()).isZero();
  }

  @Test
  void rejectsInvalidZipCode() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("zipCode", "70040-010");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isBadRequest());

    assertThat(addresses.count()).isZero();
  }

  @Test
  void rejectsBlankStreet() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("street", "  ");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isBadRequest());

    assertThat(addresses.count()).isZero();
  }

  @Test
  void rejectsUnknownCityToken() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("cityToken", "does-not-exist");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("city.not_found")));

    assertThat(addresses.count()).isZero();
  }

  @Test
  void ignoresClientSuppliedCityNameAndUf() throws Exception {
    String campinasToken = seedIbgeCity("SP", "Campinas", "3509502").getToken();
    long cityCount = cities.count();

    Map<String, Object> body = validPostal();
    body.put("cityToken", campinasToken);
    body.put("cityName", "Cidade Falsa");
    body.put("uf", "DF");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityToken").value(campinasToken))
        .andExpect(jsonPath("$.cityName").value("Campinas"))
        .andExpect(jsonPath("$.stateUf").value("SP"));

    assertThat(cities.count()).isEqualTo(cityCount);
  }

  @Test
  void acceptsBodyWithoutPlaceId() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.street").value(STREET));

    verify(places, never()).findPlaceDetails(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  void ignoresPlaceIdWhenPostalFieldsArePresent() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("placeId", "place-taguatinga");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.street").value(STREET))
        .andExpect(jsonPath("$.cityToken").value(brasiliaToken));

    verify(places, never()).findPlaceDetails(ArgumentMatchers.any(), ArgumentMatchers.any());
    assertThat(addresses.findAll().getFirst().getGooglePlaceId()).isNull();
  }

  @Test
  void persistsNeighborhoodWithoutCreatingADistrict() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("neighborhood", "Taguatinga Norte");
    body.put("number", "42");
    body.put("complement", "Bloco B apto 101");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.neighborhood").value("Taguatinga Norte"))
        .andExpect(jsonPath("$.number").value("42"))
        .andExpect(jsonPath("$.complement").value("Bloco B apto 101"));

    AddressModel saved = addresses.findAll().getFirst();
    assertThat(saved.getNeighborhood()).isEqualTo("Taguatinga Norte");
    assertThat(saved.getDistrict()).isNull();
    assertThat(districts.count()).isZero();
  }

  @Test
  void replacesTheExistingAddressInsteadOfCreatingASecondOne() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk());

    Map<String, Object> replacement = validPostal();
    replacement.put("street", "SQN 202 Bloco A");
    replacement.put("zipCode", "70832010");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(replacement)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.street").value("SQN 202 Bloco A"))
        .andExpect(jsonPath("$.zipCode").value("70832010"));

    assertThat(addresses.count()).isEqualTo(1);
  }

  @Test
  void readsBackTheOwnAddress() throws Exception {
    Map<String, Object> body = validPostal();
    body.put("neighborhood", "Asa Norte");

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/user/me/address").with(caller()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.street").value(STREET))
        .andExpect(jsonPath("$.neighborhood").value("Asa Norte"))
        .andExpect(jsonPath("$.googlePlaceId").doesNotExist());
  }

  @Test
  void returnsNotFoundWhenTheCallerHasNoAddressYet() throws Exception {
    mockMvc.perform(get("/api/user/me/address").with(caller())).andExpect(status().isNotFound());
  }

  @Test
  void rejectsUnauthenticatedDelete() throws Exception {
    mockMvc.perform(delete("/api/user/me/address")).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteClearsAddressAndSubsequentGetIsNotFound() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/user/me/address").with(caller()))
        .andExpect(status().isNoContent());

    assertThat(users.findByToken(callerUid).orElseThrow().getAddressId()).isNull();
    assertThat(addresses.count()).isZero();
    mockMvc.perform(get("/api/user/me/address").with(caller())).andExpect(status().isNotFound());
  }

  @Test
  void deleteWhenNoneIsIdempotentNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/user/me/address").with(caller()))
        .andExpect(status().isNoContent());

    assertThat(users.findByToken(callerUid).orElseThrow().getAddressId()).isNull();
  }

  @Test
  void putAfterDeleteCreatesANewAddress() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk());

    String firstToken =
        mockMvc
            .perform(get("/api/user/me/address").with(caller()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(delete("/api/user/me/address").with(caller()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(validPostal())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.street").value(STREET));

    assertThat(users.findByToken(callerUid).orElseThrow().getAddressId()).isNotNull();
    assertThat(addresses.count()).isEqualTo(1);
    String secondToken =
        mockMvc
            .perform(get("/api/user/me/address").with(caller()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(secondToken).isNotEqualTo(firstToken);
  }
}
