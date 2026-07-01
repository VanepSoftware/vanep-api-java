package br.com.vanep.client.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.Client;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.time.Instant;
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
class ClientControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;

  private MockMvc mockMvc;

  private String clientToken;
  private String ownerUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    User user = new User();
    user.setType(UserType.CLIENT);
    user.setName("Test Client");
    user.setEmail("client@vanep.com");
    user.setDocument("12345678901");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ownerUid = user.getToken();

    Client client = new Client();
    client.setUser(user);
    client = clients.save(client);

    clientToken = client.getToken();
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "admin-uid").claim("roles", List.of("ROLE_ADMIN")))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", ownerUid).claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  private JwtRequestPostProcessor otherClientJwt() {
    return jwt()
        .jwt(t -> t.claim("uid", "other-uid").claim("roles", List.of("ROLE_CLIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void listRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
  }

  @Test
  void listForbidsNonAdmin() throws Exception {
    mockMvc.perform(get("/api/clients").with(ownerJwt())).andExpect(status().isForbidden());
  }

  @Test
  void listReturnsPageForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/clients").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content[0].token").value(clientToken));
  }

  @Test
  void getByTokenRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/clients/" + clientToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void getByTokenReturns200ForAdmin() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken))
        .andExpect(jsonPath("$.email").value("client@vanep.com"));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken));
  }

  @Test
  void getByTokenReturns403ForOtherClient() throws Exception {
    mockMvc
        .perform(get("/api/clients/" + clientToken).with(otherClientJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/clients/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photo\":\"https://example.com/photo.jpg\",\"addressToken\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(clientToken));
  }

  @Test
  void updateReturns403ForOtherClient() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(otherClientJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns403ForAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/" + clientToken)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateReturns403ForNonExistentToken() throws Exception {
    mockMvc
        .perform(
            put("/api/clients/doesnotexist")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteRequiresAuthentication() throws Exception {
    mockMvc.perform(delete("/api/clients/" + clientToken)).andExpect(status().isUnauthorized());
  }

  @Test
  void deleteForbidsNonAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/clients/" + clientToken).with(ownerJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204ForAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/clients/" + clientToken).with(adminJwt()))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(delete("/api/clients/doesnotexist").with(adminJwt()))
        .andExpect(status().isNotFound());
  }
}
