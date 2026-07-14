package br.com.vanep.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  @Autowired private AssistantInviteRepository invites;

  private MockMvc mockMvc;

  private String driverEmail;
  private String driverUid;
  private DriverModel driver;

  private String otherDriverEmail;
  private String otherDriverUid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel driverUser =
        createUser(UserType.DRIVER, "Main Driver", "driver@vanep.com", "12345678909");
    driverUid = driverUser.getToken();
    driverEmail = driverUser.getEmail();
    driver = createDriver(driverUser);

    UserModel otherDriverUser =
        createUser(UserType.DRIVER, "Other Driver", "otherdriver@vanep.com", "98765432109");
    otherDriverUid = otherDriverUser.getToken();
    otherDriverEmail = otherDriverUser.getEmail();
    createDriver(otherDriverUser);
  }

  @Test
  void createInviteHappyPath() throws Exception {
    AssistantModel assistant = createAssistant("assistant@vanep.com", "11122233344");

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"assistant@vanep.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.assistantEmail").value("assistant@vanep.com"))
        .andExpect(jsonPath("$.status").value("PENDING"));

    AssistantModel updated = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(AssistantStatus.PENDING);
    assertThat(invites.findAll()).hasSize(1);
  }

  @Test
  void createInviteReturnsNotFoundWhenEmailMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@vanep.com\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createInviteReturnsConflictForClientEmail() throws Exception {
    createUser(UserType.CLIENT, "Client", "client@vanep.com", "22233344455");

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"client@vanep.com\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createInviteReturnsConflictWhenAssistantAlreadyActive() throws Exception {
    AssistantModel assistant = createAssistant("active@vanep.com", "33344455566");
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setDriver(driver);
    assistants.save(assistant);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"active@vanep.com\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createInviteReturnsConflictWhenAssistantPendingFromAnotherDriver() throws Exception {
    AssistantModel assistant = createAssistant("pending@vanep.com", "44455566677");
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    DriverModel otherDriver =
        drivers
            .findByUserId(users.findByEmail(otherDriverEmail).orElseThrow().getId())
            .orElseThrow();
    saveInvite(otherDriver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pending@vanep.com\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void resendCancelsPreviousPendingInviteFromSameDriver() throws Exception {
    AssistantModel assistant = createAssistant("resend@vanep.com", "55566677788");
    AssistantInviteModel previous =
        saveInvite(driver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resend@vanep.com\"}"))
        .andExpect(status().isCreated());

    AssistantInviteModel cancelled = invites.findById(previous.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(AssistantInviteStatus.CANCELLED);
    assertThat(
            invites.findAll().stream().filter(i -> i.getStatus() == AssistantInviteStatus.PENDING))
        .hasSize(1);
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.PENDING);
  }

  @Test
  void createInviteReturnsConflictDuringRejectionCooldown() throws Exception {
    AssistantModel assistant = createAssistant("cooldown@vanep.com", "66677788899");
    AssistantInviteModel rejected =
        saveInvite(driver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));
    rejected.setStatus(AssistantInviteStatus.REJECTED);
    rejected.setRespondedAt(Instant.now().minus(2, ChronoUnit.DAYS));
    invites.save(rejected);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cooldown@vanep.com\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void otherDriverMayInviteAfterRejectionDuringCooldown() throws Exception {
    AssistantModel assistant = createAssistant("otherinvite@vanep.com", "77788899900");
    AssistantInviteModel rejected =
        saveInvite(driver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));
    rejected.setStatus(AssistantInviteStatus.REJECTED);
    rejected.setRespondedAt(Instant.now().minus(2, ChronoUnit.DAYS));
    invites.save(rejected);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(otherDriverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"otherinvite@vanep.com\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void cancelInviteByOwnerDriver() throws Exception {
    AssistantModel assistant = createAssistant("cancel@vanep.com", "88899900011");
    AssistantInviteModel invite =
        saveInvite(driver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(delete("/api/assistants/invites/" + invite.getToken()).with(driverJwt()))
        .andExpect(status().isNoContent());

    assertThat(invites.findById(invite.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantInviteStatus.CANCELLED);
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
  }

  @Test
  void cancelInviteForbiddenForOtherDriver() throws Exception {
    AssistantModel assistant = createAssistant("forbidcancel@vanep.com", "99900011122");
    AssistantInviteModel invite =
        saveInvite(driver, assistant, Instant.now().plus(72, ChronoUnit.HOURS));

    mockMvc
        .perform(delete("/api/assistants/invites/" + invite.getToken()).with(otherDriverJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void lazyExpiryAllowsNewInvite() throws Exception {
    AssistantModel assistant = createAssistant("expired@vanep.com", "10111213141");
    AssistantInviteModel expired =
        saveInvite(driver, assistant, Instant.now().minus(1, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"expired@vanep.com\"}"))
        .andExpect(status().isCreated());

    assertThat(invites.findById(expired.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantInviteStatus.EXPIRED);
    assertThat(
            invites.findAll().stream().filter(i -> i.getStatus() == AssistantInviteStatus.PENDING))
        .hasSize(1);
  }

  @Test
  void driverCanPauseResumeAndRevokeActiveAssistant() throws Exception {
    AssistantModel assistant =
        createLinkedAssistant("linked@vanep.com", "12131415161", AssistantStatus.ACTIVE);

    mockMvc
        .perform(post("/api/assistants/" + assistant.getToken() + "/pause").with(driverJwt()))
        .andExpect(status().isNoContent());
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.INACTIVE);

    mockMvc
        .perform(post("/api/assistants/" + assistant.getToken() + "/resume").with(driverJwt()))
        .andExpect(status().isNoContent());
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.ACTIVE);

    mockMvc
        .perform(post("/api/assistants/" + assistant.getToken() + "/revoke").with(driverJwt()))
        .andExpect(status().isNoContent());

    AssistantModel revoked = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(revoked.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(revoked.getDriver()).isNull();
  }

  @Test
  void assistantCanRevokeOwnActiveLink() throws Exception {
    AssistantModel assistant =
        createLinkedAssistant("selfrevoke@vanep.com", "13141516171", AssistantStatus.ACTIVE);
    UserModel assistantUser = assistant.getUser();

    mockMvc
        .perform(post("/api/assistants/me/revoke").with(assistantJwt(assistantUser)))
        .andExpect(status().isNoContent());

    AssistantModel revoked = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(revoked.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(revoked.getDriver()).isNull();
  }

  @Test
  void listReturnsActiveAndInactiveAssistantsForDriver() throws Exception {
    createLinkedAssistant("listed-active@vanep.com", "14151617181", AssistantStatus.ACTIVE);
    createLinkedAssistant("listed-inactive@vanep.com", "15161718191", AssistantStatus.INACTIVE);
    createAssistant("unlinked@vanep.com", "16171819202");

    mockMvc
        .perform(get("/api/assistants").with(driverJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
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
            new SimpleGrantedAuthority("create_assistant_invite"),
            new SimpleGrantedAuthority("cancel_assistant_invite"),
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
            new SimpleGrantedAuthority("create_assistant_invite"),
            new SimpleGrantedAuthority("cancel_assistant_invite"),
            new SimpleGrantedAuthority("list_assistants"),
            new SimpleGrantedAuthority("pause_assistant"),
            new SimpleGrantedAuthority("resume_assistant"),
            new SimpleGrantedAuthority("revoke_assistant"));
  }

  private JwtRequestPostProcessor assistantJwt(UserModel assistantUser) {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", assistantUser.getToken())
                    .claim("roles", List.of("ROLE_ASSISTANT"))
                    .subject(assistantUser.getEmail()))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ASSISTANT"),
            new SimpleGrantedAuthority("revoke_assistant"));
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private DriverModel createDriver(UserModel user) {
    DriverModel driverModel = new DriverModel();
    driverModel.setUser(user);
    driverModel.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driverModel);
  }

  private AssistantModel createAssistant(String email, String document) {
    UserModel user = createUser(UserType.ASSISTANT, "Assistant", email, document);
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);
    return assistants.save(assistant);
  }

  private AssistantModel createLinkedAssistant(
      String email, String document, AssistantStatus status) {
    AssistantModel assistant = createAssistant(email, document);
    assistant.setDriver(driver);
    assistant.setStatus(status);
    assistant.setActivatedAt(Instant.now());
    return assistants.save(assistant);
  }

  private AssistantInviteModel saveInvite(
      DriverModel inviteDriver, AssistantModel assistant, Instant expiresAt) {
    AssistantInviteModel invite = new AssistantInviteModel();
    invite.setDriver(inviteDriver);
    invite.setAssistant(assistant);
    invite.setExpiresAt(expiresAt);
    return invites.save(invite);
  }
}
