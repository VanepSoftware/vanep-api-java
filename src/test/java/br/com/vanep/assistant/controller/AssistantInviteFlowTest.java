package br.com.vanep.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantInviteStatus;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantInviteModel;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssistantInviteFlowTest {

  private static final String ASSISTANT_PASSWORD = "secret123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private AssistantInviteRepository invites;
  @Autowired private PasswordEncoder passwordEncoder;
  @MockitoSpyBean private MailService mail;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .defaultRequest(get("/").locale(Locale.forLanguageTag("pt-BR")))
            .build();
  }

  @Test
  void signupInviteAcceptFlow() throws Exception {
    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Carla")
                .param("email", "carla@vanep.com")
                .param("password", ASSISTANT_PASSWORD)
                .param("document", "55555555555")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel assistantUser = users.findByEmail("carla@vanep.com").orElseThrow();
    AssistantModel assistant = assistants.findByUserId(assistantUser.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.UNLINKED);

    UserModel driverUser = createDriverUser("driver@vanep.com", "João");
    DriverModel driver = createDriver(driverUser);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt(driverUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"carla@vanep.com\"}"))
        .andExpect(status().isCreated());

    assistant = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.PENDING);

    AssistantInviteModel invite =
        invites
            .findByAssistantIdAndStatus(assistant.getId(), AssistantInviteStatus.PENDING)
            .orElseThrow();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> mailVariables = ArgumentCaptor.forClass(Map.class);
    verify(mail)
        .send(eq("carla@vanep.com"), any(), eq("email/assistant-invite"), mailVariables.capture());
    String link = (String) mailVariables.getValue().get("link");
    String rawSecret = link.substring(link.lastIndexOf('/') + 1);

    mockMvc.perform(get("/assistant-invite/" + rawSecret)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/assistant-invite/" + rawSecret + "/accept")
                .with(csrf())
                .with(
                    user(assistantUser.getEmail())
                        .password(ASSISTANT_PASSWORD)
                        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"))))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?invite-accepted"));

    assistant = assistants.findById(assistant.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(assistant.getDriver().getId()).isEqualTo(driver.getId());
    assertThat(invites.findById(invite.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantInviteStatus.ACCEPTED);
  }

  @Test
  void driverCanCancelPendingInviteBeforeAccept() throws Exception {
    UserModel assistantUser = createAssistantUser("cancel-flow@vanep.com");
    AssistantModel assistant = createAssistant(assistantUser);
    UserModel driverUser = createDriverUser("cancel-driver@vanep.com", "Pedro");
    createDriver(driverUser);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt(driverUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cancel-flow@vanep.com\"}"))
        .andExpect(status().isCreated());

    AssistantInviteModel invite =
        invites
            .findByAssistantIdAndStatus(assistant.getId(), AssistantInviteStatus.PENDING)
            .orElseThrow();

    mockMvc
        .perform(delete("/api/assistants/invites/" + invite.getToken()).with(driverJwt(driverUser)))
        .andExpect(status().isNoContent());

    assertThat(invites.findById(invite.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantInviteStatus.CANCELLED);
    assertThat(assistants.findById(assistant.getId()).orElseThrow().getStatus())
        .isEqualTo(AssistantStatus.UNLINKED);
  }

  @Test
  void cooldownBlocksReinviteAfterReject() throws Exception {
    UserModel assistantUser = createAssistantUser("cooldown-flow@vanep.com");
    createAssistant(assistantUser);
    UserModel driverUser = createDriverUser("cooldown-driver@vanep.com", "Ricardo");
    createDriver(driverUser);

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt(driverUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cooldown-flow@vanep.com\"}"))
        .andExpect(status().isCreated());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> mailVariables = ArgumentCaptor.forClass(Map.class);
    verify(mail)
        .send(
            eq("cooldown-flow@vanep.com"),
            any(),
            eq("email/assistant-invite"),
            mailVariables.capture());
    String link = (String) mailVariables.getValue().get("link");
    String rawSecret = link.substring(link.lastIndexOf('/') + 1);

    mockMvc
        .perform(
            post("/assistant-invite/" + rawSecret + "/reject")
                .with(csrf())
                .with(
                    user(assistantUser.getEmail())
                        .password(ASSISTANT_PASSWORD)
                        .authorities(new SimpleGrantedAuthority("ROLE_ASSISTANT"))))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(
            post("/api/assistants/invites")
                .with(driverJwt(driverUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cooldown-flow@vanep.com\"}"))
        .andExpect(status().isConflict());
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      driverJwt(UserModel driverUser) {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", driverUser.getToken())
                    .claim("roles", java.util.List.of("ROLE_DRIVER"))
                    .subject(driverUser.getEmail()))
        .authorities(
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("create_assistant_invite"),
            new SimpleGrantedAuthority("cancel_assistant_invite"),
            new SimpleGrantedAuthority("list_assistants"),
            new SimpleGrantedAuthority("pause_assistant"),
            new SimpleGrantedAuthority("resume_assistant"),
            new SimpleGrantedAuthority("revoke_assistant"));
  }

  private UserModel createDriverUser(String email, String name) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(String.valueOf(System.nanoTime()).substring(0, 11));
    user.setPassword(passwordEncoder.encode("driverpass"));
    user.setVerified(true);
    return users.save(user);
  }

  private UserModel createAssistantUser(String email) {
    UserModel user = new UserModel();
    user.setType(UserType.ASSISTANT);
    user.setName("Assistente");
    user.setEmail(email);
    user.setDocument(String.valueOf(System.nanoTime()).substring(0, 11));
    user.setPassword(passwordEncoder.encode(ASSISTANT_PASSWORD));
    user.setVerified(true);
    return users.save(user);
  }

  private DriverModel createDriver(UserModel user) {
    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }

  private AssistantModel createAssistant(UserModel user) {
    AssistantModel assistant = new AssistantModel();
    assistant.setUser(user);
    return assistants.save(assistant);
  }
}
