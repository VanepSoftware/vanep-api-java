package br.com.vanep.dependent.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
class DependentControllerTest {

  private static final String OWNER_EMAIL = "owner@vanep.com";
  private static final String OTHER_EMAIL = "other@vanep.com";
  private static final String ADMIN_EMAIL = "admin@vanep.com";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DependentRepository dependents;

  private MockMvc mockMvc;

  private Long ownerClientId;
  private String ownerClientToken;
  private Long otherClientId;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    ClientModel owner = createClient(OWNER_EMAIL, "Owner Client", "10000000001");
    ownerClientId = owner.getId();
    ownerClientToken = owner.getToken();

    ClientModel other = createClient(OTHER_EMAIL, "Other Client", "20000000002");
    otherClientId = other.getId();
  }

  private ClientModel createClient(String email, String name, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    ClientModel client = new ClientModel();
    client.setUser(user);
    return clients.save(client);
  }

  private DependentModel createDependent(Long clientId, String name, boolean isDefault) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(clientId);
    dependent.setName(name);
    dependent.setShift(Shift.MORNING);
    dependent.setDefaultDependent(isDefault);
    return dependents.save(dependent);
  }

  private JwtRequestPostProcessor jwtFor(String email, String role) {
    return jwt()
        .jwt(token -> token.subject(email).claim("roles", List.of(role)))
        .authorities(
            new SimpleGrantedAuthority(role),
            new SimpleGrantedAuthority("create_dependent"),
            new SimpleGrantedAuthority("list_dependents"),
            new SimpleGrantedAuthority("show_dependent"),
            new SimpleGrantedAuthority("update_dependent"),
            new SimpleGrantedAuthority("delete_dependent"));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwtFor(OWNER_EMAIL, "ROLE_CLIENT");
  }

  private JwtRequestPostProcessor otherJwt() {
    return jwtFor(OTHER_EMAIL, "ROLE_CLIENT");
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwtFor(ADMIN_EMAIL, "ROLE_ADMIN");
  }

  @Test
  void createReturns201ForClient() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Lucas Souza"))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.client.token").value(ownerClientToken));
  }

  @Test
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lucas Souza\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns400WhenNameMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/dependent")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsOnlyOwnDependentsForClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);
    createDependent(otherClientId, "Other Kid", true);

    mockMvc
        .perform(get("/api/dependent").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(own.getToken()));
  }

  @Test
  void getByTokenReturns200ForOwner() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }

  @Test
  void getByTokenReturns403ForOtherClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(otherJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getByTokenReturns404WhenMissing() throws Exception {
    mockMvc
        .perform(get("/api/dependent/doesnotexist").with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200ForOwner() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Old Name", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void updateReturns403ForOtherClient() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Old Name", true);

    mockMvc
        .perform(
            patch("/api/dependent/" + own.getToken())
                .with(otherJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteReturns204AndThenGetReturns404() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreReturns200AfterDeleteAndThenGetReturns200() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(delete("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }

  @Test
  void restoreReturns409ForActiveDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(post("/api/dependent/" + own.getToken() + "/restore").with(ownerJwt()))
        .andExpect(status().isConflict());
  }

  @Test
  void adminListsAllDependents() throws Exception {
    createDependent(ownerClientId, "Own Kid", true);
    createDependent(otherClientId, "Other Kid", true);

    mockMvc
        .perform(get("/api/dependent").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void adminAccessesAnyDependent() throws Exception {
    DependentModel own = createDependent(ownerClientId, "Own Kid", true);

    mockMvc
        .perform(get("/api/dependent/" + own.getToken()).with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(own.getToken()));
  }
}
