package br.com.vanep.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.repository.DriverLinkCodeRepository;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssistantFlowIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private DriverLinkCodeRepository linkCodes;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    seedAssistantRole();
  }

  @Test
  void signupWithLinkCodeThenDriverListsAssistant() throws Exception {
    String plaintextCode = "ABC234";
    seedDriverWithActiveLinkCode(plaintextCode);

    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Flow Assistant")
                .param("email", "flow@vanep.com")
                .param("password", "secret1")
                .param("document", "77777777777")
                .param("linkCode", plaintextCode)
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel assistantUser = users.findByEmail("flow@vanep.com").orElseThrow();
    var assistant = assistants.findByUserId(assistantUser.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.ACTIVE);

    UserModel driverUser = users.findByEmail("driver-flow@vanep.com").orElseThrow();
    mockMvc
        .perform(
            get("/api/assistants")
                .with(
                    jwt()
                        .jwt(
                            t ->
                                t.claim("uid", driverUser.getToken())
                                    .claim("roles", List.of("ROLE_DRIVER"))
                                    .subject(driverUser.getEmail()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_DRIVER"),
                            new SimpleGrantedAuthority("list_assistants"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].token").value(assistant.getToken()))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].name").value("Flow Assistant"));
  }

  @Test
  void oauthSignupThenConsumeActivatesLink() throws Exception {
    String plaintextCode = "XYZ789";
    seedDriverWithActiveLinkCode(plaintextCode);

    mockMvc
        .perform(
            post("/signup/complete")
                .with(
                    oidcLogin()
                        .idToken(
                            t ->
                                t.subject("g-flow")
                                    .claim("email", "oauth-flow@vanep.com")
                                    .claim("name", "OAuth Assistant")))
                .with(csrf())
                .param("type", "ASSISTANT")
                .param("document", "66666666666")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection());

    UserModel assistantUser = users.findByEmail("oauth-flow@vanep.com").orElseThrow();
    var assistant = assistants.findByUserId(assistantUser.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistant.getDriver()).isNull();

    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .with(
                    jwt()
                        .jwt(
                            t ->
                                t.claim("uid", assistantUser.getToken())
                                    .claim("roles", List.of("ROLE_ASSISTANT"))
                                    .subject(assistantUser.getEmail()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ASSISTANT"),
                            new SimpleGrantedAuthority("consume_driver_link_code")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + plaintextCode + "\"}"))
        .andExpect(status().isNoContent());

    var activated = assistants.findByUserId(assistantUser.getId()).orElseThrow();
    assertThat(activated.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(activated.getDriver()).isNotNull();
    assertThat(activated.getActivatedAt()).isNotNull();

    var consumed = linkCodes.findByCodeHash(SecureTokens.hash(plaintextCode)).orElseThrow();
    assertThat(consumed.getStatus()).isEqualTo(DriverLinkCodeStatus.CONSUMED);
  }

  private void seedAssistantRole() {
    RoleModel role = new RoleModel();
    role.setName("assistant");
    role.setDescription("Assistant access");
    role.setRoleName(RoleName.ASSISTANT);
    roles.save(role);
  }

  private void seedDriverWithActiveLinkCode(String plaintextCode) {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Flow Driver");
    driverUser.setEmail("driver-flow@vanep.com");
    driverUser.setDocument("12121212121");
    driverUser.setPassword(passwordEncoder.encode("secret1"));
    driverUser.setVerified(true);
    driverUser.setTermsAcceptedAt(Instant.now());
    driverUser = users.save(driverUser);

    DriverModel driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("100.00"));
    driver = drivers.save(driver);

    DriverLinkCodeModel code = new DriverLinkCodeModel();
    code.setDriver(driver);
    code.setCodeHash(SecureTokens.hash(plaintextCode));
    code.setStatus(DriverLinkCodeStatus.ACTIVE);
    code.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    linkCodes.save(code);
  }
}
