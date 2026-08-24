package br.com.vanep.driver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.places.exception.PlaceNotFoundException;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
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
class DriverSearchControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private CountryRepository countries;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private DistrictRepository districts;

  @MockitoBean private PlacesClient places;

  private MockMvc mockMvc;
  private String clientUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    countries.save(brasil);

    UserModel client = new UserModel();
    client.setType(UserType.CLIENT);
    client.setName("Cliente");
    client.setEmail("cliente@vanep.com");
    client.setDocument("10101010101");
    client.setVerified(true);
    client.setTermsAcceptedAt(Instant.now());
    clientUid = users.save(client).getToken();
  }

  private PlaceDetailsResponseDTO fixture(String name) throws IOException {
    String json =
        new ClassPathResource("fixtures/places/" + name + ".json")
            .getContentAsString(StandardCharsets.UTF_8);
    return MAPPER.readValue(json, PlaceDetailsResponseDTO.class);
  }

  private JwtRequestPostProcessor as(String uid) {
    return jwt().jwt(builder -> builder.claim("uid", uid).subject(uid));
  }

  /** Cria um motorista e cadastra as áreas dele pelo endpoint real, que é quem popula a árvore. */
  private String createDriverWithAreas(String email, String... placeIds) throws Exception {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Motorista " + email);
    driverUser.setEmail(email);
    driverUser.setDocument(String.valueOf(System.nanoTime()).substring(0, 11));
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);

    DriverModel driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(BigDecimal.valueOf(75));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);

    StringBuilder body = new StringBuilder("{\"areas\":[");
    for (int i = 0; i < placeIds.length; i++) {
      body.append(i > 0 ? "," : "").append("{\"placeId\":\"").append(placeIds[i]).append("\"}");
    }
    body.append("]}");

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUser.getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
        .andExpect(status().isOk());
    return driverUser.getToken();
  }

  private void stubPlaces() throws IOException {
    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));
    BDDMockito.given(places.findPlaceDetails("aguas-claras", null))
        .willReturn(fixture("df-aguas-claras"));
    BDDMockito.given(places.findPlaceDetails("ceilandia", null))
        .willReturn(fixture("df-ceilandia"));
    BDDMockito.given(places.findPlaceDetails("escola", null))
        .willReturn(fixture("df-escola-objetivo"));
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/drivers/search")
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void returnsDriverCoveringBothPoints() throws Exception {
    stubPlaces();
    createDriverWithAreas("ambos@vanep.com", "taguatinga", "aguas-claras");

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Motorista ambos@vanep.com"))
        .andExpect(jsonPath("$.content[0].token").isNotEmpty());
  }

  @Test
  void excludesDriverCoveringOnlyOnePoint() throws Exception {
    stubPlaces();
    createDriverWithAreas("so-origem@vanep.com", "taguatinga");

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void acceptsANonSchoolDestination() throws Exception {
    stubPlaces();
    createDriverWithAreas("ambos@vanep.com", "taguatinga", "aguas-claras");

    // Destino é um endereço residencial, não uma escola.
    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "aguas-claras")
                .param("destinationPlaceId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  void neverExposesResidentialAddressOfTheDriver() throws Exception {
    stubPlaces();
    createDriverWithAreas("ambos@vanep.com", "taguatinga", "aguas-claras");

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].street").doesNotExist())
        .andExpect(jsonPath("$.content[0].zipCode").doesNotExist())
        .andExpect(jsonPath("$.content[0].number").doesNotExist())
        .andExpect(jsonPath("$.content[0].complement").doesNotExist())
        .andExpect(jsonPath("$.content[0].address").doesNotExist());
  }

  @Test
  void isPaginated() throws Exception {
    stubPlaces();
    createDriverWithAreas("a@vanep.com", "taguatinga", "aguas-claras");
    createDriverWithAreas("b@vanep.com", "taguatinga", "aguas-claras");

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void rejectsAPlaceIdGoogleCannotResolve() throws Exception {
    stubPlaces();
    BDDMockito.given(places.findPlaceDetails("inexistente", null))
        .willThrow(new PlaceNotFoundException("inexistente"));

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "inexistente")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isBadRequest());
  }

  /** D3: a busca é read-only. Nem o place mais fundo pode fazer a árvore crescer. */
  @Test
  void createsNoTreeNodesWhileSearching() throws Exception {
    stubPlaces();
    createDriverWithAreas("ambos@vanep.com", "taguatinga", "aguas-claras");

    long statesBefore = states.count();
    long citiesBefore = cities.count();
    long districtsBefore = districts.count();

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "escola")
                .param("destinationPlaceId", "ceilandia"))
        .andExpect(status().isOk());

    assertThat(states.count()).isEqualTo(statesBefore);
    assertThat(cities.count()).isEqualTo(citiesBefore);
    assertThat(districts.count()).isEqualTo(districtsBefore);
  }

  @Test
  void returnsEmptyWhenTheCityIsNotInTheTreeYet() throws Exception {
    stubPlaces();

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("destinationPlaceId", "aguas-claras"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void forwardsBothSessionTokensIndependently() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", "sessao-origem"))
        .willReturn(fixture("df-taguatinga-qnl5"));
    BDDMockito.given(places.findPlaceDetails("aguas-claras", "sessao-destino"))
        .willReturn(fixture("df-aguas-claras"));

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("originPlaceId", "taguatinga")
                .param("originSessionToken", "sessao-origem")
                .param("destinationPlaceId", "aguas-claras")
                .param("destinationSessionToken", "sessao-destino"))
        .andExpect(status().isOk());

    BDDMockito.then(places).should().findPlaceDetails("taguatinga", "sessao-origem");
    BDDMockito.then(places).should().findPlaceDetails("aguas-claras", "sessao-destino");
  }
}
