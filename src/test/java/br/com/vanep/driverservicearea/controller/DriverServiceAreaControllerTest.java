package br.com.vanep.driverservicearea.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.dto.DriverServiceAreaRequestDTO;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.state.seed.StateSeeder;
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
class DriverServiceAreaControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private CountryRepository countries;
  @Autowired private StateSeeder stateSeeder;
  @Autowired private DriverServiceAreaRepository areas;

  @MockitoBean private PlacesClient places;

  private MockMvc mockMvc;
  private String driverUid;
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
    // Country and state are curated: the resolver reads them, never creates them.
    stateSeeder.seed();

    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Motorista");
    driverUser.setEmail("driver@vanep.com");
    driverUser.setDocument("11122233344");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);
    driverUid = driverUser.getToken();

    DriverModel driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(BigDecimal.valueOf(50));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    drivers.save(driver);

    UserModel clientUser = new UserModel();
    clientUser.setType(UserType.CLIENT);
    clientUser.setName("Cliente");
    clientUser.setEmail("client@vanep.com");
    clientUser.setDocument("55566677788");
    clientUser.setVerified(true);
    clientUser.setTermsAcceptedAt(Instant.now());
    clientUid = users.save(clientUser).getToken();
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

  private String body(String... placeIds) {
    StringBuilder json = new StringBuilder("{\"areas\":[");
    for (int i = 0; i < placeIds.length; i++) {
      json.append(i > 0 ? "," : "").append("{\"placeId\":\"").append(placeIds[i]).append("\"}");
    }
    return json.append("]}").toString();
  }

  @Test
  void rejectsUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/drivers/me/service-areas")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("x")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsAUserWhoIsNotADriver() throws Exception {
    mockMvc
        .perform(get("/api/drivers/me/service-areas").with(as(clientUid)))
        .andExpect(status().isForbidden());
  }

  /**
   * A fixture é um endereço de rua, que é o que o autocomplete devolve na prática: ela traz a
   * cadeia inteira (Taguatinga → Setor L Norte → QNL 5). A praça declarada tem de ser a RA. Guardar
   * a quadra faria o motorista sumir do resto de Taguatinga na busca, que casa por ancestrais (D4).
   */
  @Test
  void registersTheAdministrativeRegionAsServiceAreaNotTheBlockInsideIt() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("taguatinga")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Taguatinga"))
        .andExpect(jsonPath("$[0].cityName").value("Brasília"))
        .andExpect(jsonPath("$[0].coversWholeCity").value(false))
        .andExpect(jsonPath("$[0].token").isNotEmpty());
  }

  /**
   * Dois endereços de quadras diferentes da mesma RA declaram a mesma praça. Sem o achatamento
   * seriam duas linhas; com ele, uma — e é a deduplicação por nó que já existia que resolve.
   */
  @Test
  void collapsesTwoAddressesOfTheSameRegionIntoOneArea() throws Exception {
    BDDMockito.given(places.findPlaceDetails("qnl5", null))
        .willReturn(fixture("df-taguatinga-qnl5"));
    BDDMockito.given(places.findPlaceDetails("objetivo", null))
        .willReturn(fixture("df-escola-objetivo"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[{\"placeId\":\"qnl5\"},{\"placeId\":\"objetivo\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Taguatinga"));
  }

  /** Cada item da lista é um Place Details pago disparado na mesma requisição. */
  @Test
  void rejectsMoreAreasThanTheCap() throws Exception {
    String tooMany =
        java.util.stream.IntStream.rangeClosed(0, DriverServiceAreaRequestDTO.MAX_AREAS)
            .mapToObj(index -> "{\"placeId\":\"place-" + index + "\"}")
            .collect(java.util.stream.Collectors.joining(",", "{\"areas\":[", "]}"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooMany))
        .andExpect(status().isBadRequest());

    BDDMockito.then(places).shouldHaveNoInteractions();
  }

  @Test
  void neverExposesStreetLevelDataInTheResponse() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("taguatinga")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].street").doesNotExist())
        .andExpect(jsonPath("$[0].zipCode").doesNotExist())
        .andExpect(jsonPath("$[0].number").doesNotExist());
  }

  /** D8: no DF a cidade inteira são 5.800 km². */
  @Test
  void rejectsWholeCityInADistrictRequiringState() throws Exception {
    BDDMockito.given(places.findPlaceDetails("brasilia-inteira", null))
        .willReturn(
            new PlaceDetailsResponseDTO(
                "brasilia-inteira",
                "Brasília - DF",
                fixture("df-taguatinga-qnl5").addressComponents().stream()
                    .filter(component -> !component.types().contains("sublocality"))
                    .filter(
                        component ->
                            !component.types().contains("administrative_area_level_4")
                                && !component.types().contains("route")
                                && !component.types().contains("postal_code"))
                    .toList()));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("brasilia-inteira")))
        .andExpect(status().isBadRequest());

    assertThat(areas.count()).isZero();
  }

  @Test
  void acceptsWholeCityInAStateThatDoesNotRequireDistrict() throws Exception {
    BDDMockito.given(places.findPlaceDetails("formosa", null))
        .willReturn(fixture("interior-formosa-go"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("formosa")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Formosa"))
        .andExpect(jsonPath("$[0].coversWholeCity").value(true));
  }

  @Test
  void replacesTheWholeSetOnPut() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));
    BDDMockito.given(places.findPlaceDetails("aguas-claras", null))
        .willReturn(fixture("df-aguas-claras"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("taguatinga", "aguas-claras")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("aguas-claras")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Águas Claras"));

    assertThat(areas.count()).isEqualTo(1);
  }

  /** Dois places distintos podem resolver para o mesmo nó (D2); isso não pode virar 500. */
  @Test
  void deduplicatesPlacesThatResolveToTheSameNode() throws Exception {
    BDDMockito.given(places.findPlaceDetails("um", null)).willReturn(fixture("df-aguas-claras"));
    BDDMockito.given(places.findPlaceDetails("outro", null)).willReturn(fixture("df-aguas-claras"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("um", "outro")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void keepsThePreviousSetWhenOneItemIsRejected() throws Exception {
    BDDMockito.given(places.findPlaceDetails("aguas-claras", null))
        .willReturn(fixture("df-aguas-claras"));
    BDDMockito.given(places.findPlaceDetails("formosa", null))
        .willReturn(fixture("interior-formosa-go"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("aguas-claras")))
        .andExpect(status().isOk());

    BDDMockito.given(places.findPlaceDetails("brasilia-inteira", null))
        .willReturn(
            new PlaceDetailsResponseDTO(
                "brasilia-inteira",
                "Brasília - DF",
                fixture("df-aguas-claras").addressComponents().stream()
                    .filter(
                        component ->
                            !component.types().contains("administrative_area_level_4")
                                && !component.types().contains("route")
                                && !component.types().contains("postal_code"))
                    .toList()));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("aguas-claras", "brasilia-inteira")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/drivers/me/service-areas").with(as(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Águas Claras"));
  }

  @Test
  void rejectsAnEmptyAreaList() throws Exception {
    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"areas\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listsTheRegisteredAreas() throws Exception {
    BDDMockito.given(places.findPlaceDetails("taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/drivers/me/service-areas")
                .with(as(driverUid))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("taguatinga")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/drivers/me/service-areas").with(as(driverUid)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].stateUf").value("DF"));
  }
}
