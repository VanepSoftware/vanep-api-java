package br.com.vanep.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.role.RoleName;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class RegistrationControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private RoleRepository roles;
  @Autowired private br.com.vanep.driver.repository.DriverLinkCodeRepository linkCodes;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void signupPagesArePublic() throws Exception {
    mockMvc.perform(get("/signup")).andExpect(status().isOk());
    mockMvc.perform(get("/signup/client")).andExpect(status().isOk());
    mockMvc
        .perform(get("/signup/driver"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadastro de motorista")));
    mockMvc
        .perform(get("/signup/assistant"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Cadastro de assistente")));
  }

  @Test
  void registersClientAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Ana")
                .param("email", "ana@vanep.com")
                .param("password", "secret1")
                .param("document", "11111111111")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("ana@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.CLIENT);
    assertThat(clients.count()).isEqualTo(1);
  }

  @Test
  void registersDriverAndRedirects() throws Exception {
    mockMvc
        .perform(
            post("/signup/driver")
                .with(csrf())
                .param("name", "Bruno")
                .param("email", "bruno@vanep.com")
                .param("password", "secret1")
                .param("document", "22222222222")
                .param("city", "Taguatinga")
                .param("basePrice", "120.00")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("bruno@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.DRIVER);
    assertThat(drivers.count()).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateEmail() throws Exception {
    UserModel existing = new UserModel();
    existing.setType(UserType.CLIENT);
    existing.setName("Existing");
    existing.setEmail("dup@vanep.com");
    existing.setDocument("33333333333");
    existing.setPassword(passwordEncoder.encode("secret1"));
    users.save(existing);

    mockMvc
        .perform(
            post("/signup/client")
                .with(csrf())
                .param("name", "Other")
                .param("email", "dup@vanep.com")
                .param("password", "secret1")
                .param("document", "44444444444")
                .param("acceptTerms", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("este e-mail")));

    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void rejectsInvalidForm() throws Exception {
    mockMvc
        .perform(post("/signup/client").with(csrf()).param("email", "not-an-email"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cadastro de cliente")));

    assertThat(users.count()).isZero();
  }

  @Test
  void registersAssistantWithoutLinkCodeAsUnlinked() throws Exception {
    seedAssistantRole();

    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Carla")
                .param("email", "carla@vanep.com")
                .param("password", "secret1")
                .param("document", "88888888888")
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("carla@vanep.com").orElseThrow();
    assertThat(user.getType()).isEqualTo(UserType.ASSISTANT);
    var assistant = assistants.findByUserId(user.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.UNLINKED);
    assertThat(assistant.getDriver()).isNull();
  }

  @Test
  void registersAssistantWithValidLinkCodeAsActive() throws Exception {
    seedAssistantRole();
    String plaintextCode = "ABC234";
    seedActiveLinkCode(plaintextCode);

    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Diana")
                .param("email", "diana@vanep.com")
                .param("password", "secret1")
                .param("document", "99999999999")
                .param("linkCode", plaintextCode)
                .param("acceptTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?registered"));

    UserModel user = users.findByEmail("diana@vanep.com").orElseThrow();
    var assistant = assistants.findByUserId(user.getId()).orElseThrow();
    assertThat(assistant.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(assistant.getDriver()).isNotNull();
    assertThat(assistant.getActivatedAt()).isNotNull();
    var consumed = linkCodes.findByCodeHash(SecureTokens.hash(plaintextCode)).orElseThrow();
    assertThat(consumed.getStatus()).isEqualTo(DriverLinkCodeStatus.CONSUMED);
  }

  @Test
  void rejectsAssistantSignupWithInvalidLinkCode() throws Exception {
    seedAssistantRole();

    mockMvc
        .perform(
            post("/signup/assistant")
                .with(csrf())
                .param("name", "Elena")
                .param("email", "elena@vanep.com")
                .param("password", "secret1")
                .param("document", "10101010101")
                .param("linkCode", "BADCODE")
                .param("acceptTerms", "true"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Invalid or expired link code")));

    assertThat(users.findByEmail("elena@vanep.com")).isEmpty();
    assertThat(assistants.count()).isZero();
  }

  private void seedAssistantRole() {
    RoleModel role = new RoleModel();
    role.setName("assistant");
    role.setDescription("Assistant access");
    role.setRoleName(RoleName.ASSISTANT);
    roles.save(role);
  }

  private void seedActiveLinkCode(String plaintextCode) {
    UserModel driverUser = new UserModel();
    driverUser.setType(UserType.DRIVER);
    driverUser.setName("Driver");
    driverUser.setEmail("driver-link@vanep.com");
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
