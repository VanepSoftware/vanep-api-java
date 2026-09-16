package br.com.vanep.school.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.state.seed.StateSeeder;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
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
class SchoolResolveControllerTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private CountryRepository countries;
  @Autowired private StateRepository states;
  @Autowired private CityRepository cities;
  @Autowired private StateSeeder stateSeeder;
  @Autowired private SchoolRepository schools;
  @Autowired private MessageSource messages;

  @MockitoBean private PlacesClient places;

  private MockMvc mockMvc;
  private String callerUid;

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
    seedIbgeCity("DF", "Brasília", "5300108");

    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Responsável");
    user.setEmail("cliente@vanep.com");
    user.setDocument("12312312312");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    callerUid = users.save(user).getToken();
  }

  private PlaceDetailsResponseDTO schoolFixture() throws IOException {
    String json =
        new ClassPathResource("fixtures/places/df-escola-objetivo.json")
            .getContentAsString(StandardCharsets.UTF_8);
    PlaceDetailsResponseDTO base = MAPPER.readValue(json, PlaceDetailsResponseDTO.class);

    return new PlaceDetailsResponseDTO(
        base.id(),
        base.formattedAddress(),
        base.addressComponents(),
        base.types(),
        new PlaceDetailsResponseDTO.DisplayName("Colégio Objetivo Taguatinga", "pt-BR"));
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

  private PlaceDetailsResponseDTO unmatchedEmbuSchool() {
    return new PlaceDetailsResponseDTO(
        "place-embu-school",
        "Embu, SP",
        List.of(
            new AddressComponentDTO("Brazil", "BR", List.of("country", "political")),
            new AddressComponentDTO(
                "São Paulo", "SP", List.of("administrative_area_level_1", "political")),
            new AddressComponentDTO(
                "Embu", "Embu", List.of("administrative_area_level_2", "political"))),
        List.of("school", "educational_institution"),
        new PlaceDetailsResponseDTO.DisplayName("Escola Embu", "pt-BR"));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/schools/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsTheSchoolOnFirstResolutionAndReturns201() throws Exception {
    BDDMockito.given(places.findPlaceDetailsWithName("escola", null)).willReturn(schoolFixture());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Colégio Objetivo Taguatinga"))
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.districtName").value("QI 21"))
        .andExpect(jsonPath("$.token").isNotEmpty());

    assertThat(schools.count()).isEqualTo(1);
  }

  @Test
  void reusesTheSchoolOnSecondResolutionAndReturns200() throws Exception {
    BDDMockito.given(places.findPlaceDetailsWithName("escola", null)).willReturn(schoolFixture());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Colégio Objetivo Taguatinga"));

    assertThat(schools.count()).isEqualTo(1);
  }

  @Test
  void neverExposesTheRemovedFields() throws Exception {
    BDDMockito.given(places.findPlaceDetailsWithName("escola", null)).willReturn(schoolFixture());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cnpj").doesNotExist())
        .andExpect(jsonPath("$.phone").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.googlePlaceId").isNotEmpty());
  }

  @Test
  void rejectsAnUnmatchedGoogleCityWithCatalogMessage() throws Exception {
    BDDMockito.given(places.findPlaceDetailsWithName("embu-escola", null))
        .willReturn(unmatchedEmbuSchool());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"embu-escola\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("location.city.unmatched")));

    assertThat(schools.count()).isZero();
  }

  @Test
  void rejectsABlankPlaceId() throws Exception {
    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void forwardsTheSessionToken() throws Exception {
    BDDMockito.given(places.findPlaceDetailsWithName("escola", "sessao-1"))
        .willReturn(schoolFixture());

    mockMvc
        .perform(
            post("/api/schools/resolve")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"escola\",\"sessionToken\":\"sessao-1\"}"))
        .andExpect(status().isCreated());

    BDDMockito.then(places).should().findPlaceDetailsWithName("escola", "sessao-1");
  }
}
