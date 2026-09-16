package br.com.vanep.city.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
class CityControllerTest {
  @Autowired private WebApplicationContext context;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private MessageSource messages;

  private MockMvc mockMvc;
  private String brasiliaToken;
  private String campinasToken;
  private String stateToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

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

    StateModel saoPaulo = new StateModel();
    saoPaulo.setName("São Paulo");
    saoPaulo.setUf("SP");
    saoPaulo.setCountry(country);
    saoPaulo = states.save(saoPaulo);
    stateToken = saoPaulo.getToken();

    CityModel brasilia = new CityModel();
    brasilia.setState(distritoFederal);
    brasilia.setName("Brasília");
    brasilia.setIbgeCode("5300108");
    brasilia = cities.save(brasilia);
    brasiliaToken = brasilia.getToken();

    CityModel gama = new CityModel();
    gama.setState(distritoFederal);
    gama.setName("Gama");
    gama.setIbgeCode("5300109");
    cities.save(gama);

    CityModel campinas = new CityModel();
    campinas.setState(saoPaulo);
    campinas.setName("Campinas");
    campinas.setIbgeCode("3509502");
    campinas = cities.save(campinas);
    campinasToken = campinas.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "admin-uid")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .subject("admin@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("list_cities"),
            new SimpleGrantedAuthority("show_city"),
            new SimpleGrantedAuthority("create_city"),
            new SimpleGrantedAuthority("update_city"),
            new SimpleGrantedAuthority("delete_city"));
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

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/cities")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/cities").param("uf", "DF")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsBrasiliaForAuthenticatedClientWithoutListCities() throws Exception {
    mockMvc
        .perform(
            get("/api/cities")
                .param("uf", "DF")
                .param("search", "brasilia")
                .with(authenticatedClientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].token").value(brasiliaToken))
        .andExpect(jsonPath("$.content[0].name").value("Brasília"))
        .andExpect(jsonPath("$.content[0].stateUf").value("DF"))
        .andExpect(jsonPath("$.content[0].id").doesNotExist());
  }

  @Test
  void listWithoutUfReturns400() throws Exception {
    mockMvc
        .perform(get("/api/cities").with(authenticatedClientJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(message("city.uf.required")));
  }

  @Test
  void listUnknownUfReturns404() throws Exception {
    mockMvc
        .perform(get("/api/cities").param("uf", "XX").with(authenticatedClientJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(message("state.uf.not_found")));
  }

  @Test
  void listWithoutSearchReturnsPagedCitiesOfTheUf() throws Exception {
    mockMvc
        .perform(get("/api/cities").param("uf", "DF").with(authenticatedClientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Brasília", "Gama")))
        .andExpect(jsonPath("$.content[*].stateUf", containsInAnyOrder("DF", "DF")));
  }

  @Test
  void listDoesNotReturnCitiesFromAnotherUf() throws Exception {
    mockMvc
        .perform(get("/api/cities").param("uf", "SP").with(authenticatedClientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Campinas"))
        .andExpect(jsonPath("$.content[0].stateUf").value("SP"));
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/cities").param("uf", "SP").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(campinasToken))
        .andExpect(jsonPath("$.content[0].stateUf").value("SP"));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/cities/" + campinasToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/cities/" + campinasToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(campinasToken))
        .andExpect(jsonPath("$.name").value("Campinas"))
        .andExpect(jsonPath("$.stateToken").value(stateToken));
  }

  @Test
  void getByTokenReturns200ForAuthenticatedClientWithoutShowCity() throws Exception {
    mockMvc
        .perform(get("/api/cities/" + campinasToken).with(authenticatedClientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(campinasToken))
        .andExpect(jsonPath("$.token", notNullValue()))
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/cities/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }
}
