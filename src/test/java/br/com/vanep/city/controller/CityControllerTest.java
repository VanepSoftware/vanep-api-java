package br.com.vanep.city.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
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
class CityControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;

  private MockMvc mockMvc;
  private String cityToken;
  private String stateToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state = states.save(state);
    stateToken = state.getToken();

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    city = cities.save(city);
    cityToken = city.getToken();
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

  private JwtRequestPostProcessor noPermissionJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", "client-uid")
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject("client@vanep.com"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/cities")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsUserWithoutPermission() throws Exception {
    mockMvc.perform(get("/api/cities").with(noPermissionJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/cities").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(cityToken))
        .andExpect(jsonPath("$.content[0].stateUf").value("SP"));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/cities/" + cityToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/cities/" + cityToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cityToken))
        .andExpect(jsonPath("$.name").value("Campinas"))
        .andExpect(jsonPath("$.stateToken").value(stateToken));
  }

  @Test
  void getByTokenReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/cities/" + cityToken).with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/cities/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/cities").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(noPermissionJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Santos\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Santos\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Santos"))
        .andExpect(jsonPath("$.stateUf").value("SP"));
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenStateTokenBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Santos\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns404WhenStateMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Santos\",\"stateToken\":\"doesnotexist\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createReturns409WhenNameDuplicatedInState() throws Exception {
    mockMvc
        .perform(
            post("/api/cities")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Campinas\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/cities/" + cityToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Valinhos\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Valinhos"));
  }

  @Test
  void updateReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/cities/" + cityToken)
                .with(noPermissionJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/cities/doesnotexist")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"stateToken\":\"" + stateToken + "\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/cities/" + cityToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/cities/" + cityToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(delete("/api/cities/" + cityToken).with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/cities/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200AfterDelete() throws Exception {
    mockMvc.perform(delete("/api/cities/" + cityToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/cities/" + cityToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(cityToken));
  }

  @Test
  void restoreReturns409WhenNotDeleted() throws Exception {
    mockMvc
        .perform(post("/api/cities/" + cityToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void restoreReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(post("/api/cities/doesnotexist/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(post("/api/cities/" + cityToken + "/restore").with(noPermissionJwt()))
        .andExpect(status().isForbidden());
  }
}
