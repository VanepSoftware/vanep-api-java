package br.com.vanep.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssistantInviteWebControllerTest {

  private static final String PASSWORD = "secret123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private AssistantInviteRepository invites;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;
  private DriverModel driver;
  private UserModel assistantUser;
  private AssistantModel assistant;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .defaultRequest(get("/").locale(Locale.forLanguageTag("pt-BR")))
            .build();

    UserModel driverUser = createUser(UserType.DRIVER, "João Motorista", "driver@vanep.com");
    driver = createDriver(driverUser, "Taguatinga", new BigDecimal("4.50"));
    assistantUser = createUser(UserType.ASSISTANT, "Maria Assistente", "assistant@vanep.com");
    assistant = createAssistant(assistantUser);
  }

  @Test
  void getValidInvitePageShowsDriverInfo() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(get("/assistant-invite/" + rawSecret))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("João Motorista")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Taguatinga")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("4.50")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Faça login")));
  }

  @Test
  void getExpiredInvitePageShowsExpired() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().minus(1, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(get("/assistant-invite/" + rawSecret))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("não está mais disponível")));

    assertThat(invites.findAll().getFirst().getStatus()).isEqualTo(AssistantInviteStatus.EXPIRED);
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
  }

  @Test
  void getInvalidInvitePageShowsInvalid() throws Exception {
    mockMvc
        .perform(get("/assistant-invite/invalid-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("inválido")));
  }

  @Test
  void acceptWhenLoggedInAsInvitedAssistantActivatesLink() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(
            post("/assistant-invite/" + rawSecret + "/accept").with(csrf()).with(assistantUser()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?invite-accepted"));

    AssistantModel updated = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(updated.getDriver().getId()).isEqualTo(driver.getId());
    assertThat(updated.getActivatedAt()).isNotNull();
    assertThat(invites.findAll().getFirst().getStatus()).isEqualTo(AssistantInviteStatus.ACCEPTED);
  }

  @Test
  void rejectWhenLoggedInAsInvitedAssistantRejectsAndUnlinks() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    mockMvc
        .perform(
            post("/assistant-invite/" + rawSecret + "/reject").with(csrf()).with(assistantUser()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?invite-rejected"));

    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
    assertThat(invites.findAll().getFirst().getStatus()).isEqualTo(AssistantInviteStatus.REJECTED);
  }

  @Test
  void acceptWhenLoggedInAsWrongUserIsForbidden() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    UserModel otherAssistantUser =
        createUser(UserType.ASSISTANT, "Outro Assistente", "other@vanep.com");
    createAssistant(otherAssistantUser);

    mockMvc
        .perform(
            post("/assistant-invite/" + rawSecret + "/accept")
                .with(csrf())
                .with(
                    user(otherAssistantUser.getEmail())
                        .password(PASSWORD)
                        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"))))
        .andExpect(status().isForbidden());

    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.PENDING);
  }

  @Test
  void getInvitePageWhenLoggedInAsWrongUserShowsError() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));
    assistant.setStatus(AssistantStatus.PENDING);
    assistants.save(assistant);

    UserModel otherAssistantUser =
        createUser(UserType.ASSISTANT, "Outro Assistente", "wrong@vanep.com");
    createAssistant(otherAssistantUser);

    mockMvc
        .perform(
            get("/assistant-invite/" + rawSecret)
                .with(
                    user(otherAssistantUser.getEmail())
                        .password(PASSWORD)
                        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("assistente convidado")));
  }

  @Test
  void postAcceptUnauthenticatedRedirectsToLogin() throws Exception {
    String rawSecret = SecureTokens.generate();
    savePendingInvite(rawSecret, Instant.now().plus(72, ChronoUnit.HOURS));

    mockMvc
        .perform(post("/assistant-invite/" + rawSecret + "/accept").with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .UserRequestPostProcessor
      assistantUser() {
    return user(assistantUser.getEmail())
        .password(PASSWORD)
        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"));
  }

  private UserModel createUser(UserType type, String name, String email) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(String.valueOf(System.nanoTime()).substring(0, 11));
    user.setPassword(passwordEncoder.encode(PASSWORD));
    user.setVerified(true);
    return users.save(user);
  }

  private DriverModel createDriver(UserModel user, String city, BigDecimal rating) {
    DriverModel driverModel = new DriverModel();
    driverModel.setUser(user);
    driverModel.setCity(city);
    driverModel.setRating(rating);
    driverModel.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driverModel);
  }

  private AssistantModel createAssistant(UserModel user) {
    AssistantModel assistantModel = new AssistantModel();
    assistantModel.setUser(user);
    return assistants.save(assistantModel);
  }

  private AssistantInviteModel savePendingInvite(String rawSecret, Instant expiresAt) {
    AssistantInviteModel invite = new AssistantInviteModel();
    invite.setDriver(driver);
    invite.setAssistant(assistant);
    invite.setLinkTokenHash(SecureTokens.hash(rawSecret));
    invite.setExpiresAt(expiresAt);
    return invites.save(invite);
  }
}
