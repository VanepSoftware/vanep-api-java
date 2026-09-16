package br.com.vanep.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.enums.AuthCodePurpose;
import br.com.vanep.auth.mail.MailService;
import br.com.vanep.auth.password.PasswordResetTokenRepository;
import br.com.vanep.auth.password.model.PasswordResetTokenModel;
import br.com.vanep.auth.token.SecureCodes;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.EmailVerificationTokenRepository;
import br.com.vanep.auth.verification.model.EmailVerificationTokenModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EmailCodeApiControllerTest {

  private static final String EMAIL = "code-user@vanep.com";
  private static final String CODE = "123456";
  private static final String PASSWORD = "secret123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private EmailVerificationTokenRepository verificationTokens;
  @Autowired private PasswordResetTokenRepository resetTokens;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SecureCodes codes;
  @MockitoBean private MailService mail;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void theRightCodeVerifiesTheAccount() throws Exception {
    UserModel user = persistUser(false);
    persistVerificationCode(user, CODE, Duration.ofHours(24));

    submitCode(CODE).andExpect(status().isNoContent());

    assertThat(users.findByEmail(EMAIL).orElseThrow().isVerified()).isTrue();
  }

  @Test
  void aWrongCodeIsRejectedAndTheFifthAttemptBurnsTheCode() throws Exception {
    UserModel user = persistUser(false);
    persistVerificationCode(user, CODE, Duration.ofHours(24));

    for (int attempt = 0; attempt < 5; attempt++) {
      submitCode("000000")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("invalid_code"));
    }

    submitCode(CODE).andExpect(status().isBadRequest());
    assertThat(users.findByEmail(EMAIL).orElseThrow().isVerified()).isFalse();
  }

  @Test
  void anExpiredOrReplacedCodeIsRejected() throws Exception {
    UserModel user = persistUser(false);
    persistVerificationCode(user, CODE, Duration.ofHours(-1));

    submitCode(CODE).andExpect(status().isBadRequest());

    persistVerificationCode(user, "999999", Duration.ofHours(24));
    verificationTokens
        .findFirstByUserIdOrderByCreatedAtDesc(user.getId())
        .ifPresent(token -> assertThat(token.getCodeHash()).isNotNull());
    submitCode(CODE).andExpect(status().isBadRequest());
  }

  @Test
  void anUnknownEmailAVerifiedAccountAndAPendingChangeAllAnswerTheSame() throws Exception {
    mockMvc
        .perform(verifyRequest("nobody@vanep.com", CODE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_code"));

    UserModel verified = persistUser(true);
    persistVerificationCode(verified, CODE, Duration.ofHours(24));
    submitCode(CODE).andExpect(status().isBadRequest());

    verified.setVerified(false);
    verified.setPendingEmail("new@vanep.com");
    users.save(verified);
    submitCode(CODE).andExpect(status().isBadRequest());
    assertThat(users.findByEmail(EMAIL).orElseThrow().getEmail()).isEqualTo(EMAIL);
  }

  @Test
  void resendAndForgotAlwaysAnswerAccepted() throws Exception {
    persistUser(false);

    mockMvc
        .perform(emailRequest("/api/auth/email/verify/resend", EMAIL))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(emailRequest("/api/auth/email/verify/resend", "nobody@vanep.com"))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(emailRequest("/api/auth/password/forgot", EMAIL))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(emailRequest("/api/auth/password/forgot", "nobody@vanep.com"))
        .andExpect(status().isAccepted());

    verify(mail, times(1)).send(eq(EMAIL), anyString(), eq("email/verification"), anyMap());
    verify(mail, times(1)).send(eq(EMAIL), anyString(), eq("email/password-reset"), anyMap());
    verify(mail, never()).send(eq("nobody@vanep.com"), anyString(), anyString(), anyMap());
  }

  @Test
  void aSecondRequestInsideTheCooldownSendsNothingAndStillAnswersAccepted() throws Exception {
    persistUser(false);

    mockMvc
        .perform(emailRequest("/api/auth/password/forgot", EMAIL))
        .andExpect(status().isAccepted());
    mockMvc
        .perform(emailRequest("/api/auth/password/forgot", EMAIL))
        .andExpect(status().isAccepted());

    verify(mail, times(1)).send(eq(EMAIL), anyString(), eq("email/password-reset"), anyMap());
  }

  @Test
  void theRightCodeResetsThePassword() throws Exception {
    UserModel user = persistUser(true);
    persistResetCode(user, CODE);

    mockMvc
        .perform(resetRequest(EMAIL, CODE, "brand-new-password"))
        .andExpect(status().isNoContent());

    assertThat(
            passwordEncoder.matches(
                "brand-new-password", users.findByEmail(EMAIL).orElseThrow().getPassword()))
        .isTrue();
    assertThat(
            passwordEncoder.matches(PASSWORD, users.findByEmail(EMAIL).orElseThrow().getPassword()))
        .isFalse();
  }

  @Test
  void aShortNewPasswordIsRejectedWithoutConsumingTheCode() throws Exception {
    UserModel user = persistUser(true);
    persistResetCode(user, CODE);

    mockMvc
        .perform(resetRequest(EMAIL, CODE, "1234567"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')]").exists());

    mockMvc.perform(resetRequest(EMAIL, CODE, "12345678")).andExpect(status().isNoContent());
  }

  @Test
  void aWrongResetCodeAnswersInvalidCode() throws Exception {
    UserModel user = persistUser(true);
    persistResetCode(user, CODE);

    mockMvc
        .perform(resetRequest(EMAIL, "000000", "brand-new-password"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_code"));

    mockMvc
        .perform(resetRequest("nobody@vanep.com", CODE, "brand-new-password"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid_code"));
  }

  private org.springframework.test.web.servlet.ResultActions submitCode(String code)
      throws Exception {
    return mockMvc.perform(verifyRequest(EMAIL, code));
  }

  private org.springframework.test.web.servlet.RequestBuilder verifyRequest(
      String email, String code) {
    return post("/api/auth/email/verify")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
  }

  private org.springframework.test.web.servlet.RequestBuilder emailRequest(
      String path, String email) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"" + email + "\"}");
  }

  private org.springframework.test.web.servlet.RequestBuilder resetRequest(
      String email, String code, String newPassword) {
    return post("/api/auth/password/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"email\":\""
                + email
                + "\",\"code\":\""
                + code
                + "\",\"newPassword\":\""
                + newPassword
                + "\"}");
  }

  private UserModel persistUser(boolean verified) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Code User");
    user.setEmail(EMAIL);
    user.setDocument("39053344705");
    user.setPassword(passwordEncoder.encode(PASSWORD));
    user.setVerified(verified);
    return users.save(user);
  }

  private void persistVerificationCode(UserModel user, String code, Duration validFor) {
    EmailVerificationTokenModel token = new EmailVerificationTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(UUID.randomUUID().toString()));
    token.setCodeHash(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, user.getId(), code));
    token.setExpiresAt(Instant.now().plus(validFor));
    verificationTokens.save(token);
  }

  private void persistResetCode(UserModel user, String code) {
    PasswordResetTokenModel token = new PasswordResetTokenModel();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(UUID.randomUUID().toString()));
    token.setCodeHash(codes.hmac(AuthCodePurpose.PASSWORD_RESET, user.getId(), code));
    token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
    resetTokens.save(token);
  }
}
