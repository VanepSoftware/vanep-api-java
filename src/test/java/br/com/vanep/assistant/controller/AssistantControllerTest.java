package br.com.vanep.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
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
class AssistantControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private ClientRepository clients;

  private MockMvc mockMvc;
  private String driverEmail;
  private String driverUid;
  private DriverModel driver;

  private String otherDriverEmail;
  private String otherDriverUid;

  private String assistantEmail;
  private String assistantUid;
  private String assistantToken;
  private AssistantModel assistant;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver");
    driverUser.setEmail("driver@vanep.com");
    driverUser.setDocument("12345678909");
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);
    driverEmail = driverUser.getEmail();
    driverUid = driverUser.getToken();

    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);

    UserModel otherDriverUser = new UserModel();
    otherDriverUser.setType(UserType.DRIVER);
    otherDriverUser.setName("Other Driver");
    otherDriverUser.setEmail("other@vanep.com");
    otherDriverUser.setDocument("11111111111");
    otherDriverUser.setVerified(true);
    otherDriverUser.setTermsAcceptedAt(Instant.now());
    otherDriverUser = users.save(otherDriverUser);
    otherDriverEmail = otherDriverUser.getEmail();
    otherDriverUid = otherDriverUser.getToken();

    DriverModel otherDriver = new DriverModel();
    otherDriver.setUser(otherDriverUser);
    otherDriver.setBasePrice(new BigDecimal("120.00"));
    drivers.save(otherDriver);

    UserModel assistantUser = new UserModel();
    assistantUser.setType(UserType.ASSISTANT);
    assistantUser.setName("Assistant");
    assistantUser.setEmail("assistant@vanep.com");
    assistantUser.setDocument("98765432109");
    assistantUser.setVerified(true);
    assistantUser.setTermsAcceptedAt(Instant.now());
    assistantUser = users.save(assistantUser);
    assistantEmail = assistantUser.getEmail();
    assistantUid = assistantUser.getToken();

    assistant = new AssistantModel();
    assistant.setUser(assistantUser);
    assistant.setDriver(driver);
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setActivatedAt(Instant.now());
    assistant = assistants.save(assistant);
    assistantToken = assistant.getToken();
  }

  private JwtRequestPostProcessor driverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", driverUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(driverEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("list_assistants"),
            new SimpleGrantedAuthority("pause_assistant"),
            new SimpleGrantedAuthority("resume_assistant"),
            new SimpleGrantedAuthority("revoke_assistant"));
  }

  private JwtRequestPostProcessor otherDriverJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", otherDriverUid)
                    .claim("roles", List.of("ROLE_DRIVER"))
                    .subject(otherDriverEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("pause_assistant"),
            new SimpleGrantedAuthority("resume_assistant"),
            new SimpleGrantedAuthority("revoke_assistant"));
  }

  private JwtRequestPostProcessor assistantJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", assistantUid)
                    .claim("roles", List.of("ROLE_ASSISTANT"))
                    .subject(assistantEmail))
        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"));
  }

  private JwtRequestPostProcessor clientJwt() {
    UserModel clientUser = new UserModel();
    clientUser.setType(UserType.CLIENT);
    clientUser.setName("Client");
    clientUser.setEmail("client@vanep.com");
    clientUser.setDocument("55555555555");
    clientUser.setVerified(true);
    clientUser.setTermsAcceptedAt(Instant.now());
    UserModel saved = users.save(clientUser);
    ClientModel client = new ClientModel();
    client.setUser(saved);
    clients.save(client);

    return jwt()
        .jwt(
            t ->
                t.claim("uid", saved.getToken())
                    .claim("roles", List.of("ROLE_CLIENT"))
                    .subject(saved.getEmail()))
        .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
  }

  @Test
  void getMeReturnsProfile() throws Exception {
    mockMvc
        .perform(get("/api/assistants/me").with(assistantJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(assistantToken))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.name").value("Assistant"));
  }

  @Test
  void updateMeUpdatesPhoto() throws Exception {
    mockMvc
        .perform(
            put("/api/assistants/me")
                .with(assistantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photo\":\"https://cdn.example/photo.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value("https://cdn.example/photo.jpg"));

    AssistantModel updated = assistants.findByToken(assistantToken).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getPhoto())
        .isEqualTo("https://cdn.example/photo.jpg");
  }

  @Test
  void getMeForbidsDriver() throws Exception {
    mockMvc.perform(get("/api/assistants/me").with(driverJwt())).andExpect(status().isForbidden());
  }

  @Test
  void getMeForbidsClient() throws Exception {
    mockMvc.perform(get("/api/assistants/me").with(clientJwt())).andExpect(status().isForbidden());
  }

  @Test
  void updateMeForbidsDriver() throws Exception {
    mockMvc
        .perform(
            put("/api/assistants/me")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"photo\":\"https://cdn.example/photo.jpg\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getMeRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/assistants/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsEmptyWhenNoAssistants() throws Exception {
    assistants.delete(assistant);

    mockMvc
        .perform(get("/api/assistants").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getByTokenIsNotMapped() throws Exception {
    mockMvc
        .perform(get("/api/assistants/" + assistantToken).with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listReturnsDriverAssistants() throws Exception {
    mockMvc
        .perform(get("/api/assistants").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(assistantToken))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void listForbidsAssistant() throws Exception {
    mockMvc.perform(get("/api/assistants").with(assistantJwt())).andExpect(status().isForbidden());
  }

  @Test
  void pauseReturns204ForOwner() throws Exception {
    mockMvc
        .perform(post("/api/assistants/" + assistantToken + "/pause").with(driverJwt()))
        .andExpect(status().isNoContent());

    AssistantModel updated = assistants.findByToken(assistantToken).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(AssistantStatus.INACTIVE);
  }

  @Test
  void pauseReturns403ForOtherDriver() throws Exception {
    mockMvc
        .perform(post("/api/assistants/" + assistantToken + "/pause").with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void pauseReturns403ForAssistant() throws Exception {
    mockMvc
        .perform(post("/api/assistants/" + assistantToken + "/pause").with(assistantJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void resumeReturns204ForOwner() throws Exception {
    assistant.setStatus(AssistantStatus.INACTIVE);
    assistants.save(assistant);

    mockMvc
        .perform(post("/api/assistants/" + assistantToken + "/resume").with(driverJwt()))
        .andExpect(status().isNoContent());

    AssistantModel updated = assistants.findByToken(assistantToken).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(AssistantStatus.ACTIVE);
  }

  @Test
  void driverRevokeReturns204() throws Exception {
    mockMvc
        .perform(post("/api/assistants/" + assistantToken + "/revoke").with(driverJwt()))
        .andExpect(status().isNoContent());

    AssistantModel updated = assistants.findByToken(assistantToken).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
    org.assertj.core.api.Assertions.assertThat(updated.getDriver()).isNull();
  }

  @Test
  void assistantSelfRevokeReturns204() throws Exception {
    mockMvc
        .perform(post("/api/assistants/me/revoke").with(assistantJwt()))
        .andExpect(status().isNoContent());

    AssistantModel updated = assistants.findByToken(assistantToken).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updated.getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
    org.assertj.core.api.Assertions.assertThat(updated.getDriver()).isNull();
  }

  @Test
  void pauseReturns404ForUnknownToken() throws Exception {
    mockMvc
        .perform(post("/api/assistants/unknown-token/pause").with(driverJwt()))
        .andExpect(status().isNotFound());
  }
}
