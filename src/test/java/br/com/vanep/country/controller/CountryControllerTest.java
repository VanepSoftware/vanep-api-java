package br.com.vanep.country.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class CountryControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private CountryRepository countryRepository;

  private MockMvc mockMvc;
  private String countryToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countryRepository.save(country);
    countryToken = country.getToken();
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
            new SimpleGrantedAuthority("list_countries"),
            new SimpleGrantedAuthority("show_country"),
            new SimpleGrantedAuthority("create_country"),
            new SimpleGrantedAuthority("update_country"),
            new SimpleGrantedAuthority("delete_country"),
            new SimpleGrantedAuthority("restore_country"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(
            new SimpleGrantedAuthority("ROLE_CLIENT"),
            new SimpleGrantedAuthority("list_countries"),
            new SimpleGrantedAuthority("show_country"));
  }

  private JwtRequestPostProcessor noPermissionJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "no-perm-uid")
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject("driver@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/countries")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/countries").with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listReturnsCountriesForAuthorizedUser() throws Exception {
    mockMvc
        .perform(get("/api/countries").with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Brasil"))
        .andExpect(jsonPath("$[0].token").value(countryToken))
        .andExpect(jsonPath("$[0].isoCode").value("BR"))
        .andExpect(jsonPath("$[0].phoneCode").value("+55"))
        .andExpect(jsonPath("$[0].currency").value("BRL"))
        .andExpect(jsonPath("$[0].locale").value("pt-BR"));
  }

  @Test
  void showReturnsCountryByToken() throws Exception {
    mockMvc
        .perform(get("/api/countries/" + countryToken).with(clientJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Brasil"))
        .andExpect(jsonPath("$.token").value(countryToken))
        .andExpect(jsonPath("$.isoCode").value("BR"))
        .andExpect(jsonPath("$.phoneCode").value("+55"))
        .andExpect(jsonPath("$.currency").value("BRL"))
        .andExpect(jsonPath("$.locale").value("pt-BR"));
  }

  @Test
  void showReturnsNotFoundForInvalidToken() throws Exception {
    mockMvc
        .perform(get("/api/countries/invalid-token").with(clientJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createPersistsNewCountryForAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/countries")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Argentina\", \"isoCode\": \"AR\", \"phoneCode\": \"+54\", \"currency\": \"ARS\", \"locale\": \"es-AR\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Argentina"))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.isoCode").value("AR"))
        .andExpect(jsonPath("$.phoneCode").value("+54"))
        .andExpect(jsonPath("$.currency").value("ARS"))
        .andExpect(jsonPath("$.locale").value("es-AR"));
  }

  @Test
  void createReturnsConflictForDuplicateName() throws Exception {
    mockMvc
        .perform(
            post("/api/countries")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Brasil\", \"isoCode\": \"AR\", \"phoneCode\": \"+54\", \"currency\": \"ARS\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createReturnsConflictForDuplicateIsoCode() throws Exception {
    mockMvc
        .perform(
            post("/api/countries")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"Argentina\", \"isoCode\": \"BR\", \"phoneCode\": \"+54\", \"currency\": \"ARS\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateModifiesCountryNameAndOtherFields() throws Exception {
    mockMvc
        .perform(
            put("/api/countries/" + countryToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"República Federativa do Brasil\", \"isoCode\": \"BR\", \"phoneCode\": \"+55\", \"currency\": \"BRL\", \"locale\": \"pt-BR\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("República Federativa do Brasil"))
        .andExpect(jsonPath("$.isoCode").value("BR"))
        .andExpect(jsonPath("$.phoneCode").value("+55"));
  }

  @Test
  void deleteSoftDeletesCountry() throws Exception {
    mockMvc
        .perform(delete("/api/countries/" + countryToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/countries/" + countryToken).with(clientJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReactivatesSoftDeletedCountry() throws Exception {
    // Soft delete first
    mockMvc
        .perform(delete("/api/countries/" + countryToken).with(adminJwt()))
        .andExpect(status().isNoContent());

    // Restore it
    mockMvc
        .perform(post("/api/countries/" + countryToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(countryToken))
        .andExpect(jsonPath("$.active").value(true));

    // Should be retrievable again
    mockMvc
        .perform(get("/api/countries/" + countryToken).with(clientJwt()))
        .andExpect(status().isOk());
  }
}
