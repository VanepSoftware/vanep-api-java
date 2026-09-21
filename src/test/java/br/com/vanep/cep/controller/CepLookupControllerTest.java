package br.com.vanep.cep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.cep.client.ViaCepClient;
import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.cep.exception.ViaCepLookupException;
import br.com.vanep.cep.exception.ViaCepNotFoundException;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class CepLookupControllerTest {
  private static final String BRASILIA_CEP = "70040010";
  private static final String UNKNOWN_CEP = "99999999";

  @Autowired private WebApplicationContext context;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private AddressRepository addresses;
  @Autowired private MessageSource messages;

  @Value("${vanep.viacep.base-url}")
  private String viaCepBaseUrl;

  @MockitoBean private ViaCepClient viaCepClient;

  @MockitoBean(name = "viaCepRateLimiter")
  private RateLimiter viaCepRateLimiter;

  private MockMvc mockMvc;
  private String brasiliaToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(viaCepRateLimiter.tryAcquire(anyString())).thenReturn(true);

    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country = countries.save(country);

    StateModel distritoFederal = new StateModel();
    distritoFederal.setName("Distrito Federal");
    distritoFederal.setUf("DF");
    distritoFederal.setCountry(country);
    distritoFederal = states.save(distritoFederal);

    CityModel brasilia = new CityModel();
    brasilia.setState(distritoFederal);
    brasilia.setName("Brasília");
    brasilia.setIbgeCode("5300108");
    brasilia = cities.save(brasilia);
    brasiliaToken = brasilia.getToken();
  }

  private JwtRequestPostProcessor authenticatedClientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  private ViaCepResponseDTO brasiliaViaCep() {
    return new ViaCepResponseDTO(
        "70040-010", "Quadra SBN Quadra 1", "Asa Norte", "Brasília", "DF", "5300108", null);
  }

  @Test
  void testProfileNeverPointsAtARealViaCepHost() {
    String host = URI.create(viaCepBaseUrl).getHost();

    assertThat(host)
        .as("test profile base-url must be unreachable (rule 50)")
        .isIn("localhost", "127.0.0.1", "::1");
    assertThat(viaCepBaseUrl).doesNotContain("viacep.com.br");
  }

  @Test
  void lookupRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/cep/" + BRASILIA_CEP)).andExpect(status().isUnauthorized());
    verify(viaCepClient, never()).findByCep(anyString());
  }

  @Test
  void lookupReturnsBrasiliaCityTokenForKnownCep() throws Exception {
    when(viaCepClient.findByCep(BRASILIA_CEP)).thenReturn(brasiliaViaCep());

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cityToken").value(brasiliaToken))
        .andExpect(jsonPath("$.cityName").value("Brasília"))
        .andExpect(jsonPath("$.uf").value("DF"))
        .andExpect(jsonPath("$.street").value("Quadra SBN Quadra 1"))
        .andExpect(jsonPath("$.neighborhood").value("Asa Norte"))
        .andExpect(jsonPath("$.id").doesNotExist());

    assertThat(addresses.count()).isZero();
  }

  @Test
  void lookupInvalidFormatReturns400() throws Exception {
    mockMvc
        .perform(get("/api/cep/70040-010").with(authenticatedClientJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("cep.invalid")));

    verify(viaCepClient, never()).findByCep(anyString());
    assertThat(addresses.count()).isZero();
  }

  @Test
  void lookupUnknownCepReturns404() throws Exception {
    when(viaCepClient.findByCep(UNKNOWN_CEP)).thenThrow(new ViaCepNotFoundException(UNKNOWN_CEP));

    mockMvc
        .perform(get("/api/cep/" + UNKNOWN_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("cep.not_found")));

    assertThat(addresses.count()).isZero();
  }

  @Test
  void lookupTransportFailureReturns503() throws Exception {
    when(viaCepClient.findByCep(BRASILIA_CEP))
        .thenThrow(new ViaCepLookupException("Failed to reach ViaCEP."));

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value(message("cep.lookup_failed")));

    assertThat(addresses.count()).isZero();
  }

  @Test
  void lookupUnknownIbgeCodeReturns404() throws Exception {
    when(viaCepClient.findByCep(BRASILIA_CEP))
        .thenReturn(
            new ViaCepResponseDTO(
                "70040-010", "Rua X", "Centro", "Cidade Fantasma", "XX", "0000000", null));

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("cep.ibge.not_found")));

    assertThat(addresses.count()).isZero();
  }

  @Test
  void lookupBlankIbgeReturns404EvenWhenACityHasNoIbgeCode() throws Exception {
    CityModel withoutIbge = new CityModel();
    withoutIbge.setState(states.findAll().getFirst());
    withoutIbge.setName("Cidade Sem Codigo");
    cities.save(withoutIbge);
    when(viaCepClient.findByCep(BRASILIA_CEP))
        .thenReturn(
            new ViaCepResponseDTO("70040-010", "Rua X", "Centro", "Brasília", "DF", " ", null));

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("cep.ibge.not_found")));
  }

  @Test
  void lookupMissingIbgeReturns404EvenWhenACityHasNoIbgeCode() throws Exception {
    CityModel withoutIbge = new CityModel();
    withoutIbge.setState(states.findAll().getFirst());
    withoutIbge.setName("Cidade Sem Codigo");
    cities.save(withoutIbge);
    when(viaCepClient.findByCep(BRASILIA_CEP))
        .thenReturn(
            new ViaCepResponseDTO("70040-010", "Rua X", "Centro", "Brasília", "DF", null, null));

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("cep.ibge.not_found")));
  }

  @Test
  void lookupRateLimitReturns429WithoutCallingViaCep() throws Exception {
    when(viaCepRateLimiter.tryAcquire(anyString())).thenReturn(false);

    mockMvc
        .perform(get("/api/cep/" + BRASILIA_CEP).with(authenticatedClientJwt()))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.detail").value(message("cep.rate_limited")));

    verify(viaCepClient, never()).findByCep(anyString());
  }
}
