package br.com.vanep.school.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.state.seed.StateSeeder;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
  @Autowired private StateSeeder stateSeeder;
  @Autowired private SchoolRepository schools;

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
    // Country and state are curated: the resolver reads them, never creates them.
    stateSeeder.seed();

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
    // O mask de escola pede displayName (SKU Pro) — a fixture foi coletada com o
    // mask estreito, então o nome é acrescentado aqui.
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
