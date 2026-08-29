package br.com.vanep.driver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
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
  @Autowired private DriverServiceAreaRepository areas;

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

  private DriverModel saveDriver(String email) {
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
    return drivers.save(driver);
  }

  /** Cadastra pelo endpoint real, que é quem popula a árvore a partir do place. */
  private String createDriverWithAreas(String email, String... placeIds) throws Exception {
    DriverModel driver = saveDriver(email);

    StringBuilder body = new StringBuilder("{\"areas\":[");
    for (int index = 0; index < placeIds.length; index++) {
      body.append(index > 0 ? "," : "")
          .append("{\"placeId\":\"")
          .append(placeIds[index])
          .append("\"}");
    }
    body.append("]}");

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driver.getUser().getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
        .andExpect(status().isOk());
    return driver.getUser().getToken();
  }

  /**
   * Área ligada direto ao nó, sem passar pelo endpoint. Necessário para montar níveis que a policy
   * D8 recusaria — cidade inteira no DF, por exemplo — em um teste que é sobre ordenação, não sobre
   * aquela regra.
   */
  private DriverModel giveArea(String email, DistrictModel district) {
    DriverModel driver = saveDriver(email);
    DriverServiceAreaModel area = new DriverServiceAreaModel();
    area.setDriver(driver);
    area.setCity(cities.findAll().getFirst());
    area.setDistrict(district);
    areas.save(area);
    return driver;
  }

  private DistrictModel districtNamed(String name) {
    return districts.findAll().stream()
        .filter(district -> district.getName().equals(name))
        .findFirst()
        .orElseThrow();
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
        .perform(get("/api/drivers/search").param("placeId", "taguatinga"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void returnsDriverWhoseAreaIsExactlyThePoint() throws Exception {
    stubPlaces();
    createDriverWithAreas("exato@vanep.com", "taguatinga");

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Motorista exato@vanep.com"));
  }

  @Test
  void excludesDriverInASiblingRegion() throws Exception {
    stubPlaces();
    createDriverWithAreas("aguas@vanep.com", "aguas-claras");

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  /**
   * O requisito central: quanto mais perto do ponto, mais cedo aparece. Os quatro níveis são
   * montados explicitamente porque com árvore rasa a ordenação pareceria funcionar mesmo quebrada.
   */
  @Test
  void ranksFromTheMostSpecificAreaToTheWholeCity() throws Exception {
    stubPlaces();
    createDriverWithAreas("qnl5@vanep.com", "taguatinga");

    DriverModel setorDriver = giveArea("setor@vanep.com", districtNamed("Setor L Norte"));
    DriverModel taguatingaDriver = giveArea("taguatinga@vanep.com", districtNamed("Taguatinga"));
    DriverModel cidadeDriver = giveArea("cidade@vanep.com", null);

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(4))
        .andExpect(jsonPath("$.content[0].name").value("Motorista qnl5@vanep.com"))
        .andExpect(jsonPath("$.content[1].name").value("Motorista setor@vanep.com"))
        .andExpect(jsonPath("$.content[2].name").value("Motorista taguatinga@vanep.com"))
        .andExpect(jsonPath("$.content[3].name").value("Motorista cidade@vanep.com"));

    assertThat(setorDriver.getId()).isNotNull();
    assertThat(taguatingaDriver.getId()).isNotNull();
    assertThat(cidadeDriver.getId()).isNotNull();
  }

  @Test
  void wholeCityDriverIsReturnedLastNotFilteredOut() throws Exception {
    stubPlaces();
    createDriverWithAreas("qnl5@vanep.com", "taguatinga");
    giveArea("cidade@vanep.com", null);

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[1].name").value("Motorista cidade@vanep.com"));
  }

  /** Quem cadastrou o nó exato e também a cidade inteira merece a posição do nó exato. */
  @Test
  void driverKeepsTheBestRankAmongTheirAreas() throws Exception {
    stubPlaces();
    String email = "ambos@vanep.com";
    createDriverWithAreas(email, "taguatinga");
    DriverModel driver = drivers.findAll().getFirst();
    DriverServiceAreaModel cityWide = new DriverServiceAreaModel();
    cityWide.setDriver(driver);
    cityWide.setCity(cities.findAll().getFirst());
    areas.save(cityWide);

    giveArea("cidade@vanep.com", null);

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Motorista " + email));
  }

  /// As regiões atendidas são dado público (nós da árvore, sem logradouro) e o app
  /// as mostra abaixo do nome do motorista.
  @Test
  void returnsTheRegionsTheDriverCovers() throws Exception {
    stubPlaces();
    createDriverWithAreas("exato@vanep.com", "taguatinga", "aguas-claras");

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].serviceAreas.length()").value(2))
        .andExpect(jsonPath("$.content[0].serviceAreas", hasItems("QNL 5", "Águas Claras")));
  }

  @Test
  void reportsAWholeCityAreaByTheCityName() throws Exception {
    stubPlaces();
    createDriverWithAreas("qnl5@vanep.com", "taguatinga");
    giveArea("cidade@vanep.com", null);

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[1].serviceAreas[0]").value("Brasília"));
  }

  @Test
  void neverExposesResidentialAddressOfTheDriver() throws Exception {
    stubPlaces();
    createDriverWithAreas("exato@vanep.com", "taguatinga");

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].street").doesNotExist())
        .andExpect(jsonPath("$.content[0].zipCode").doesNotExist())
        .andExpect(jsonPath("$.content[0].number").doesNotExist())
        .andExpect(jsonPath("$.content[0].complement").doesNotExist())
        .andExpect(jsonPath("$.content[0].address").doesNotExist());
  }

  @Test
  void paginatesPreservingTheRankOrder() throws Exception {
    stubPlaces();
    createDriverWithAreas("qnl5@vanep.com", "taguatinga");
    giveArea("cidade@vanep.com", null);

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("placeId", "taguatinga")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Motorista qnl5@vanep.com"));

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("placeId", "taguatinga")
                .param("size", "1")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Motorista cidade@vanep.com"));
  }

  /**
   * Escola entra na mesma caixa que endereço. O motorista precisa cobrir Taguatinga, e não QNL 5:
   * registrar o place "taguatinga" o coloca no nó mais fundo daquela cadeia (D2), que é irmão do
   * ramo da escola.
   */
  @Test
  void acceptsASchoolAsTheSearchedPlace() throws Exception {
    stubPlaces();
    createDriverWithAreas("qnl5@vanep.com", "taguatinga");
    giveArea("taguatinga@vanep.com", districtNamed("Taguatinga"));

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "escola"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Motorista taguatinga@vanep.com"));
  }

  @Test
  void rejectsAPlaceIdGoogleCannotResolve() throws Exception {
    stubPlaces();
    BDDMockito.given(places.findPlaceDetails("inexistente", null))
        .willThrow(new PlaceNotFoundException("inexistente"));

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "inexistente"))
        .andExpect(status().isBadRequest());
  }

  /** D3: a busca é read-only. Nem o place mais fundo pode fazer a árvore crescer. */
  @Test
  void createsNoTreeNodesWhileSearching() throws Exception {
    stubPlaces();
    createDriverWithAreas("exato@vanep.com", "taguatinga");

    long statesBefore = states.count();
    long citiesBefore = cities.count();
    long districtsBefore = districts.count();

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "ceilandia"))
        .andExpect(status().isOk());

    assertThat(states.count()).isEqualTo(statesBefore);
    assertThat(cities.count()).isEqualTo(citiesBefore);
    assertThat(districts.count()).isEqualTo(districtsBefore);
  }

  @Test
  void returnsEmptyWhenTheCityIsNotInTheTreeYet() throws Exception {
    stubPlaces();

    mockMvc
        .perform(get("/api/drivers/search").with(as(clientUid)).param("placeId", "taguatinga"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void forwardsTheSessionTokenToThePlacesClient() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", "sessao-1"))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            get("/api/drivers/search")
                .with(as(clientUid))
                .param("placeId", "taguatinga")
                .param("sessionToken", "sessao-1"))
        .andExpect(status().isOk());

    BDDMockito.then(places).should().findPlaceDetails("taguatinga", "sessao-1");
  }
}
