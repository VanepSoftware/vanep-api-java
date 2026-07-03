package br.com.vanep.role.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
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
class RoleControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private RoleRepository roles;

  private MockMvc mockMvc;
  private String roleToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    RoleModel role = new RoleModel();
    role.setName("Test Role");
    role.setDescription("A test role");
    role = roles.save(role);
    roleToken = role.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid"))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private JwtRequestPostProcessor clientJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "client-uid"))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsNonAdmin() throws Exception {
    mockMvc.perform(get("/api/roles").with(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/roles").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(roleToken));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/roles/" + roleToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/roles/" + roleToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(roleToken))
        .andExpect(jsonPath("$.name").value("Test Role"));
  }

  @Test
  void getByTokenReturns403ForNonAdmin() throws Exception {
    mockMvc
        .perform(get("/api/roles/" + roleToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/roles/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/roles").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsNonAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/roles")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/roles")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Role\",\"description\":\"desc\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("New Role"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/roles")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/roles/" + roleToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Role\",\"description\":\"updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Role"));
  }

  @Test
  void updateReturns403ForNonAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/roles/" + roleToken)
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"description\":\"y\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/roles/doesnotexist")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"description\":\"y\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/roles/" + roleToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/roles/" + roleToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForNonAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/roles/" + roleToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/roles/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200AfterDelete() throws Exception {
    mockMvc.perform(delete("/api/roles/" + roleToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/roles/" + roleToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(roleToken));
  }

  @Test
  void restoreReturns404WhenNotDeleted() throws Exception {
    mockMvc
        .perform(post("/api/roles/" + roleToken + "/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns403ForNonAdmin() throws Exception {
    mockMvc
        .perform(post("/api/roles/" + roleToken + "/restore").with(clientJwt()))
        .andExpect(status().isForbidden());
  }
}
