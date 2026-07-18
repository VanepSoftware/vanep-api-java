package br.com.vanep.school.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
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
class SchoolControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private SchoolRepository schools;

  private MockMvc mockMvc;
  private String schoolToken;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    SchoolModel school = new SchoolModel();
    school.setName("Escola Teste");
    school.setCnpj("11222333000181");
    school.setPhone("11999990000");
    school.setEmail("contato@escolateste.com.br");
    school = schools.save(school);
    schoolToken = school.getToken();
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
            new SimpleGrantedAuthority("list_schools"),
            new SimpleGrantedAuthority("show_school"),
            new SimpleGrantedAuthority("create_school"),
            new SimpleGrantedAuthority("update_school"),
            new SimpleGrantedAuthority("delete_school"),
            new SimpleGrantedAuthority("restore_school"));
  }

  private JwtRequestPostProcessor clientJwt() {
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
    mockMvc.perform(get("/api/schools")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsUserWithoutPermission() throws Exception {
    mockMvc.perform(get("/api/schools").with(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/schools").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].token").value(schoolToken));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/schools/" + schoolToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/schools/" + schoolToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(schoolToken))
        .andExpect(jsonPath("$.name").value("Escola Teste"))
        .andExpect(jsonPath("$.cnpj").value("11222333000181"));
  }

  @Test
  void getByTokenReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(get("/api/schools/" + schoolToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/schools/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/schools").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createForbidsUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nova Escola\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createReturns201ForAdmin() throws Exception {
    String body =
        """
        {
          "name": "Nova Escola",
          "cnpj": "44555666000172",
          "phone": "11988887777",
          "email": "contato@novaescola.com.br"
        }
        """;
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.name").value("Nova Escola"))
        .andExpect(jsonPath("$.cnpj").value("44555666000172"));
  }

  @Test
  void createReturns400WhenNameBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns400WhenCnpjInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nova Escola\",\"cnpj\":\"123\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createReturns409WhenCnpjDuplicated() throws Exception {
    mockMvc
        .perform(
            post("/api/schools")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outra Escola\",\"cnpj\":\"11222333000181\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/schools/" + schoolToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Escola Atualizada\",\"cnpj\":\"11222333000181\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Escola Atualizada"));
  }

  @Test
  void updateReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/schools/" + schoolToken)
                .with(clientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/schools/doesnotexist")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/schools/" + schoolToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/schools/" + schoolToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(delete("/api/schools/" + schoolToken).with(clientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/schools/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200AfterDelete() throws Exception {
    mockMvc.perform(delete("/api/schools/" + schoolToken).with(adminJwt()));

    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(schoolToken));
  }

  @Test
  void restoreReturns409WhenNotDeleted() throws Exception {
    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(adminJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void restoreReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(post("/api/schools/doesnotexist/restore").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns403ForUserWithoutPermission() throws Exception {
    mockMvc
        .perform(post("/api/schools/" + schoolToken + "/restore").with(clientJwt()))
        .andExpect(status().isForbidden());
  }
}
