package br.com.vanep.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.EmailVerificationTokenRepository;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.Gender;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProfileControllerTest {

  private static final String EMAIL = "profile@vanep.com";
  private static final String DOCUMENT = "12345678901";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 15);

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private EmailVerificationTokenRepository verificationTokens;
  @MockitoSpyBean private MailService mail;

  private MockMvc mockMvc;
  private String uid;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Tester");
    user.setEmail(EMAIL);
    user.setDocument(DOCUMENT);
    user.setBirthDate(BIRTH_DATE);
    user.setGender(Gender.FEMALE);
    user.setPhone("11999999999");
    user.setVerified(true);
    user = users.save(user);
    uid = user.getToken();
  }

  @Test
  void patchMeRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patchMeHappyPathUpdatesNamePhoneGenderAndLeavesDocumentBirthDate() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Updated Name",
                      "phone": "11888888888",
                      "gender": "MALE"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"))
        .andExpect(jsonPath("$.phone").value("11888888888"))
        .andExpect(jsonPath("$.gender").value("MALE"))
        .andExpect(jsonPath("$.document").value(DOCUMENT))
        .andExpect(jsonPath("$.birthDate").value("1990-05-15"))
        .andExpect(jsonPath("$.email").value(EMAIL))
        .andExpect(jsonPath("$.token").value(uid));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Updated Name");
    assertThat(reloaded.getPhone()).isEqualTo("11888888888");
    assertThat(reloaded.getGender()).isEqualTo(Gender.MALE);
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
    assertThat(reloaded.getLastNameChangeAt()).isNotNull();
    assertThat(reloaded.getLastPhoneChangeAt()).isNotNull();
  }

  @Test
  void patchMeBlankPhoneReturns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("phone_blank"))
        .andExpect(jsonPath("$.field").value("phone"))
        .andExpect(jsonPath("$.message").value(notNullValue()))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getPhone()).isEqualTo("11999999999");
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
  }

  @Test
  void patchMeNameCooldownReturns409WithStructuredBody() throws Exception {
    UserModel user = users.findByToken(uid).orElseThrow();
    Instant lastChange = Instant.now().minus(5, ChronoUnit.DAYS);
    user.setLastNameChangeAt(lastChange);
    users.save(user);

    mockMvc
        .perform(
            patch("/api/user/me")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Blocked Name\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("cooldown"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.message").value(notNullValue()))
        .andExpect(jsonPath("$.retryAfter").value(notNullValue()));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Tester");
    assertThat(reloaded.getDocument()).isEqualTo(DOCUMENT);
    assertThat(reloaded.getBirthDate()).isEqualTo(BIRTH_DATE);
  }

  @Test
  void emailChangeRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@vanep.com\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void emailChangeHappyPathSetsPendingAndSendsToNewAddress() throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@vanep.com\"}"))
        .andExpect(status().isNoContent());

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getPendingEmail()).isEqualTo("new@vanep.com");
    assertThat(reloaded.getEmail()).isEqualTo(EMAIL);
    assertThat(reloaded.getLastEmailChangeAt()).isNull();

    ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
    verify(mail).send(eq("new@vanep.com"), anyString(), eq("email/email-change"), vars.capture());
    assertThat(vars.getValue().get("link").toString()).contains("/verify-email?token=");
  }

  @Test
  void emailChangeDuplicateReturns409StructuredBody() throws Exception {
    UserModel other = new UserModel();
    other.setType(UserType.CLIENT);
    other.setName("Other");
    other.setEmail("taken@vanep.com");
    other.setDocument("98765432100");
    other.setBirthDate(BIRTH_DATE);
    other.setGender(Gender.MALE);
    other.setVerified(true);
    users.save(other);

    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"taken@vanep.com\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("email_duplicate"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.message").value(notNullValue()))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getPendingEmail()).isNull();
    assertThat(reloaded.getEmail()).isEqualTo(EMAIL);
  }

  @Test
  void emailChangeSameAsCurrentReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("email_same"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.message").value(notNullValue()))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailChangeBlankReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("email_required"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailChangeInvalidReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("email_invalid"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailChangeThenVerifyPromotesPending() throws Exception {
    String rawToken = requestEmailChangeAndCaptureToken("promote@vanep.com");

    mockMvc
        .perform(get("/verify-email").param("token", rawToken))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verified"));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getEmail()).isEqualTo("promote@vanep.com");
    assertThat(reloaded.getPendingEmail()).isNull();
    assertThat(reloaded.isVerified()).isTrue();
    assertThat(reloaded.getLastEmailChangeAt()).isNotNull();
  }

  @Test
  void emailChangeAThenBOldLinkFailsAndLatestConfirmsB() throws Exception {
    String tokenA = requestEmailChangeAndCaptureToken("a@vanep.com");
    String tokenB = requestEmailChangeAndCaptureToken("b@vanep.com");

    UserModel afterReplace = users.findByToken(uid).orElseThrow();
    assertThat(afterReplace.getPendingEmail()).isEqualTo("b@vanep.com");
    assertThat(afterReplace.getEmail()).isEqualTo(EMAIL);

    List<EmailVerificationTokenModel> allTokens = verificationTokens.findAll();
    assertThat(allTokens).hasSize(2);
    assertThat(allTokens.stream().filter(t -> t.getConsumedAt() != null).count()).isEqualTo(1);

    mockMvc.perform(get("/verify-email").param("token", tokenA)).andExpect(status().isOk());

    UserModel afterOldLink = users.findByToken(uid).orElseThrow();
    assertThat(afterOldLink.getEmail()).isEqualTo(EMAIL);
    assertThat(afterOldLink.getPendingEmail()).isEqualTo("b@vanep.com");

    mockMvc
        .perform(get("/verify-email").param("token", tokenB))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verified"));

    UserModel afterConfirm = users.findByToken(uid).orElseThrow();
    assertThat(afterConfirm.getEmail()).isEqualTo("b@vanep.com");
    assertThat(afterConfirm.getPendingEmail()).isNull();
    assertThat(afterConfirm.getLastEmailChangeAt()).isNotNull();
  }

  @Test
  void verifyPendingDuplicateRedirectsWithEmailTaken() throws Exception {
    UserModel other = new UserModel();
    other.setType(UserType.CLIENT);
    other.setName("Other");
    other.setEmail("dup-confirm@vanep.com");
    other.setDocument("11122233344");
    other.setBirthDate(BIRTH_DATE);
    other.setGender(Gender.MALE);
    other.setVerified(true);
    users.save(other);

    UserModel user = users.findByToken(uid).orElseThrow();
    user.setPendingEmail("dup-confirm@vanep.com");
    users.save(user);

    String raw = "manual-verify-token";
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    verificationTokens.save(token);

    mockMvc
        .perform(get("/verify-email").param("token", raw))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?email_taken"));

    UserModel reloaded = users.findByToken(uid).orElseThrow();
    assertThat(reloaded.getEmail()).isEqualTo(EMAIL);
    assertThat(reloaded.getPendingEmail()).isEqualTo("dup-confirm@vanep.com");
  }

  @SuppressWarnings("unchecked")
  private String requestEmailChangeAndCaptureToken(String newEmail) throws Exception {
    mockMvc
        .perform(
            post("/api/user/me/email-change")
                .with(jwt().jwt(token -> token.claim("uid", uid).subject(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + newEmail + "\"}"))
        .andExpect(status().isNoContent());

    ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
    verify(mail, Mockito.atLeastOnce())
        .send(eq(newEmail), anyString(), eq("email/email-change"), vars.capture());
    String link = vars.getValue().get("link").toString();
    return link.substring(link.indexOf("token=") + "token=".length());
  }
}
