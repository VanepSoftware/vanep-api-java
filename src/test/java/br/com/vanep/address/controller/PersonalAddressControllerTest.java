package br.com.vanep.address.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.places.exception.PlaceNotFoundException;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * O {@link PlacesClient} é mockado: nenhum teste chama a API real (regra 50). As respostas vêm das
 * fixtures da fase 1.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PersonalAddressControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private AddressRepository addresses;
  @Autowired private CountryRepository countries;
  @Autowired private StateSeeder stateSeeder;
  @Autowired private DistrictRepository districts;

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
    user.setType(UserType.DRIVER);
    user.setName("Motorista Sem Endereço");
    user.setEmail("driver@vanep.com");
    user.setDocument("98765432100");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    callerUid = users.save(user).getToken();
  }

  private PlaceDetailsResponseDTO fixture(String name) throws IOException {
    String json =
        new ClassPathResource("fixtures/places/" + name + ".json")
            .getContentAsString(StandardCharsets.UTF_8);
    return MAPPER.readValue(json, PlaceDetailsResponseDTO.class);
  }

  private AddressComponentDTO component(String longText, String shortText, String... types) {
    return new AddressComponentDTO(longText, shortText, List.of(types));
  }

  /** Um place que para na RA: cidade e Águas Claras, e nada abaixo disso. */
  private PlaceDetailsResponseDTO administrativeRegionOnly() {
    return new PlaceDetailsResponseDTO(
        "ra-inteira",
        "Águas Claras, Brasília - DF",
        List.of(
            component("Brazil", "BR", "country", "political"),
            component("Distrito Federal", "DF", "administrative_area_level_1", "political"),
            component("Brasília", "Brasília", "administrative_area_level_2", "political"),
            component("Águas Claras", "Águas Claras", "administrative_area_level_4", "political")));
  }

  private JwtRequestPostProcessor caller() {
    return jwt().jwt(builder -> builder.claim("uid", callerUid).subject(callerUid));
  }

  @Test
  void rejectsUnauthenticatedWrite() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"any\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsUnauthenticatedRead() throws Exception {
    mockMvc.perform(get("/api/user/me/address")).andExpect(status().isUnauthorized());
  }

  @Test
  void createsAddressFromPlaceIdAndLinksItToTheTree() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-taguatinga\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.districtName").value("QNL 5"))
        .andExpect(jsonPath("$.stateUf").value("DF"))
        .andExpect(jsonPath("$.countryIsoCode").value("BR"))
        .andExpect(jsonPath("$.token").isNotEmpty());

    assertThat(districts.count()).isEqualTo(3);
    assertThat(users.findByToken(callerUid).orElseThrow().getAddressId()).isNotNull();
  }

  @Test
  void exposesOpaqueTokensAndNeverInternalIds() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-taguatinga\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.cityId").doesNotExist())
        .andExpect(jsonPath("$.districtId").doesNotExist())
        .andExpect(jsonPath("$.cityToken").isNotEmpty())
        .andExpect(jsonPath("$.districtToken").isNotEmpty());
  }

  @Test
  void preservesTheUserSuppliedComplementAndNumber() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"placeId\":\"place-taguatinga\",\"number\":\"42\",\"complement\":\"Bloco B"
                        + " apto 101\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value("42"))
        .andExpect(jsonPath("$.complement").value("Bloco B apto 101"));
  }

  /** O cliente manda só o placeId; qualquer componente que ele enviar é ignorado. */
  @Test
  void ignoresAddressComponentsSuppliedByTheClient() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"placeId\":\"place-taguatinga\",\"cityName\":\"Cidade Falsa\",\"street\":\"Rua"
                        + " Inventada\",\"districtName\":\"Bairro Plantado\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.street").value("Setor L Norte Qnl 5 Conjunto I J"));

    assertThat(districts.findAll())
        .extracting(district -> district.getName())
        .doesNotContain("Bairro Plantado");
  }

  @Test
  void rejectsAPlaceIdGoogleCannotResolve() throws Exception {
    BDDMockito.given(places.findPlaceDetails("inexistente", null))
        .willThrow(new PlaceNotFoundException("inexistente"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"inexistente\"}"))
        .andExpect(status().isBadRequest());

    assertThat(addresses.count()).isZero();
  }

  @Test
  void rejectsABlankPlaceId() throws Exception {
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"  \"}"))
        .andExpect(status().isBadRequest());
  }

  /**
   * A recusa é de place que para na região, não de place sem {@code route}. Esta fixture sintética
   * tem cidade e RA e nada abaixo: descreve Águas Claras inteira, não uma casa dentro dela.
   */
  @Test
  void rejectsAPlaceThatStopsAtTheAdministrativeRegion() throws Exception {
    BDDMockito.given(places.findPlaceDetails("ra-inteira", null))
        .willReturn(administrativeRegionOnly());

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"ra-inteira\"}"))
        .andExpect(status().isBadRequest());

    assertThat(addresses.count()).isZero();
  }

  /**
   * O contra-exemplo que o 400 anterior escondia: a fixture df-ceilandia é a QNM 17, um pin
   * legítimo, e o Google não lhe deu {@code route} nenhum — a quadra veio como {@code
   * sublocality_level_3}. Antes virava 400; agora o logradouro sai da própria quadra.
   */
  @Test
  void acceptsABlockAddressThatTheGoogleDidNotLabelAsRoute() throws Exception {
    BDDMockito.given(places.findPlaceDetails("qnm-17", null)).willReturn(fixture("df-ceilandia"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"qnm-17\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.street").value("QNM 17"));
  }

  @Test
  void forwardsTheSessionTokenToThePlacesClient() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", "sessao-1"))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-taguatinga\",\"sessionToken\":\"sessao-1\"}"))
        .andExpect(status().isOk());

    BDDMockito.then(places).should().findPlaceDetails("place-taguatinga", "sessao-1");
  }

  @Test
  void replacesTheExistingAddressInsteadOfCreatingASecondOne() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));
    BDDMockito.given(places.findPlaceDetails("place-aguas-claras", null))
        .willReturn(fixture("df-aguas-claras"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-taguatinga\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-aguas-claras\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.districtName").value("Águas Claras"));

    assertThat(addresses.count()).isEqualTo(1);
  }

  @Test
  void readsBackTheOwnAddress() throws Exception {
    BDDMockito.given(places.findPlaceDetails("place-taguatinga", null))
        .willReturn(fixture("df-taguatinga-qnl5"));

    mockMvc
        .perform(
            put("/api/user/me/address")
                .with(caller())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeId\":\"place-taguatinga\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/user/me/address").with(caller()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityName").value("Brasília"));
  }

  @Test
  void returnsNotFoundWhenTheCallerHasNoAddressYet() throws Exception {
    mockMvc.perform(get("/api/user/me/address").with(caller())).andExpect(status().isNotFound());
  }
}
