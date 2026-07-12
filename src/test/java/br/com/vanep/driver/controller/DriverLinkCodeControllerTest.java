package br.com.vanep.driver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.driver.DriverLinkCodeStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverLinkCodeModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.repository.DriverLinkCodeRepository;
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
class DriverLinkCodeControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private AssistantRepository assistants;
  @Autowired private DriverLinkCodeRepository linkCodes;

  private MockMvc mockMvc;
  private String driverEmail;
  private String driverUid;
  private DriverModel driver;

  private String assistantEmail;
  private String assistantUid;
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
    assistant.setStatus(AssistantStatus.UNLINKED);
    assistant = assistants.save(assistant);
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
            new SimpleGrantedAuthority("create_driver_link_code"),
            new SimpleGrantedAuthority("cancel_driver_link_code"));
  }

  private JwtRequestPostProcessor assistantJwt() {
    return jwt()
        .jwt(
            t ->
                t.claim("uid", assistantUid)
                    .claim("roles", List.of("ROLE_ASSISTANT"))
                    .subject(assistantEmail))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ASSISTANT"),
            new SimpleGrantedAuthority("consume_driver_link_code"));
  }

  @Test
  void generateRequiresAuthentication() throws Exception {
    mockMvc.perform(post("/api/driver-link-codes")).andExpect(status().isUnauthorized());
  }

  @Test
  void generateReturns201With24HourExpiry() throws Exception {
    mockMvc
        .perform(post("/api/driver-link-codes").with(driverJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.expiresAt").exists());

    DriverLinkCodeModel active =
        linkCodes
            .findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE)
            .orElseThrow();
    assertThat(active.getExpiresAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
    assertThat(active.getExpiresAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
  }

  @Test
  void generateCancelsPreviousActiveCode() throws Exception {
    String firstCode = "ABC234";
    DriverLinkCodeModel existing = new DriverLinkCodeModel();
    existing.setDriver(driver);
    existing.setCodeHash(SecureTokens.hash(firstCode));
    existing.setStatus(DriverLinkCodeStatus.ACTIVE);
    existing.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    linkCodes.save(existing);

    mockMvc
        .perform(post("/api/driver-link-codes").with(driverJwt()))
        .andExpect(status().isCreated());

    DriverLinkCodeModel cancelled =
        linkCodes.findByCodeHash(SecureTokens.hash(firstCode)).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo(DriverLinkCodeStatus.CANCELLED);
    assertThat(linkCodes.findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE))
        .isPresent();
  }

  @Test
  void cancelCurrentCodeReturns204() throws Exception {
    DriverLinkCodeModel active = new DriverLinkCodeModel();
    active.setDriver(driver);
    active.setCodeHash(SecureTokens.hash("XYZ789"));
    active.setStatus(DriverLinkCodeStatus.ACTIVE);
    active.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    linkCodes.save(active);

    mockMvc
        .perform(delete("/api/driver-link-codes/current").with(driverJwt()))
        .andExpect(status().isNoContent());

    assertThat(linkCodes.findByDriverIdAndStatus(driver.getId(), DriverLinkCodeStatus.ACTIVE))
        .isEmpty();
  }

  @Test
  void cancelReturns404WhenNoActiveCode() throws Exception {
    mockMvc
        .perform(delete("/api/driver-link-codes/current").with(driverJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void consumeRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABC234\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void consumeActivatesUnlinkedAssistant() throws Exception {
    String plaintextCode = "ABC234";
    seedActiveLinkCode(plaintextCode);

    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .with(assistantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + plaintextCode + "\"}"))
        .andExpect(status().isNoContent());

    AssistantModel updated = assistants.findByUserId(assistant.getUser().getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(AssistantStatus.ACTIVE);
    assertThat(updated.getDriver()).isNotNull();
    assertThat(updated.getActivatedAt()).isNotNull();

    DriverLinkCodeModel consumed =
        linkCodes.findByCodeHash(SecureTokens.hash(plaintextCode)).orElseThrow();
    assertThat(consumed.getStatus()).isEqualTo(DriverLinkCodeStatus.CONSUMED);
  }

  @Test
  void consumeReturns400ForInvalidCode() throws Exception {
    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .with(assistantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BADCDE\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void consumeReturns409WhenAssistantNotUnlinked() throws Exception {
    assistant.setStatus(AssistantStatus.ACTIVE);
    assistant.setDriver(driver);
    assistants.save(assistant);

    String plaintextCode = "ABC234";
    seedActiveLinkCode(plaintextCode);

    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .with(assistantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + plaintextCode + "\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void consumeFailsWhenCodeAlreadyConsumedAtSignup() throws Exception {
    String plaintextCode = "ABC234";
    DriverLinkCodeModel consumed = new DriverLinkCodeModel();
    consumed.setDriver(driver);
    consumed.setCodeHash(SecureTokens.hash(plaintextCode));
    consumed.setStatus(DriverLinkCodeStatus.CONSUMED);
    consumed.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    consumed.setConsumedAt(Instant.now());
    consumed.setConsumedByAssistantId(99L);
    linkCodes.save(consumed);

    mockMvc
        .perform(
            post("/api/driver-link-codes/consume")
                .with(assistantJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + plaintextCode + "\"}"))
        .andExpect(status().isBadRequest());
  }

  private void seedActiveLinkCode(String plaintextCode) {
    DriverLinkCodeModel code = new DriverLinkCodeModel();
    code.setDriver(driver);
    code.setCodeHash(SecureTokens.hash(plaintextCode));
    code.setStatus(DriverLinkCodeStatus.ACTIVE);
    code.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
    linkCodes.save(code);
  }
}
