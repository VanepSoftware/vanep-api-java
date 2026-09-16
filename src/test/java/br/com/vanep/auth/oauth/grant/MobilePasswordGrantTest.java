package br.com.vanep.auth.oauth.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.security.LoginAttemptService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MobilePasswordGrantTest {

  private static final String GRANT_TYPE = "urn:vanep:params:oauth:grant-type:password";
  private static final String MOBILE_CLIENT_ID = "vanep-mobile";
  private static final String EMAIL = "grant-user@vanep.com";
  private static final String GOOGLE_ONLY_EMAIL = "google-only@vanep.com";
  private static final String PASSWORD = "secret123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private LoginAttemptService loginAttempts;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    loginAttempts.loginSucceeded(EMAIL);
    loginAttempts.loginSucceeded(GOOGLE_ONLY_EMAIL);
    loginAttempts.loginSucceeded("nobody@vanep.com");
  }

  private UserModel persistGoogleOnlyUser() {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Google Only");
    user.setEmail(GOOGLE_ONLY_EMAIL);
    user.setDocument("52998224725");
    user.setVerified(true);
    return users.save(user);
  }

  private UserModel persistUser(boolean verified, boolean withLocalPassword) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Grant User");
    user.setEmail(EMAIL);
    user.setDocument("39053344705");
    user.setPassword(withLocalPassword ? passwordEncoder.encode(PASSWORD) : null);
    user.setVerified(verified);
    return users.save(user);
  }

  @Test
  void verifiedAccountGetsTokensAndHasItsLoginRecorded() throws Exception {
    persistUser(true, true);
    loginAttempts.loginFailed(EMAIL);

    JsonNode tokens = grant(MOBILE_CLIENT_ID, EMAIL, PASSWORD, status().isOk());

    assertThat(tokens.hasNonNull("access_token")).isTrue();
    assertThat(tokens.hasNonNull("refresh_token")).isTrue();
    assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
    assertThat(tokens.get("expires_in").asLong()).isPositive();
    assertThat(users.findByEmail(EMAIL).orElseThrow().getLastLoginAt())
        .isNotNull()
        .isBeforeOrEqualTo(Instant.now());
    assertThat(loginAttempts.isBlocked(EMAIL)).isFalse();
  }

  @Test
  void unknownEmailWrongPasswordAndGoogleOnlyAccountAreIndistinguishable() throws Exception {
    persistUser(true, true);
    persistGoogleOnlyUser();

    JsonNode unknownEmail =
        grant(MOBILE_CLIENT_ID, "nobody@vanep.com", PASSWORD, status().isBadRequest());
    JsonNode wrongPassword =
        grant(MOBILE_CLIENT_ID, EMAIL, "wrong-password", status().isBadRequest());
    JsonNode googleOnly =
        grant(MOBILE_CLIENT_ID, GOOGLE_ONLY_EMAIL, PASSWORD, status().isBadRequest());

    assertThat(unknownEmail.get("error").asText()).isEqualTo("invalid_grant");
    assertThat(wrongPassword).isEqualTo(unknownEmail);
    assertThat(googleOnly).isEqualTo(unknownEmail);
  }

  @Test
  void unverifiedAccountIsOnlyReportedAfterTheRightPassword() throws Exception {
    persistUser(false, true);

    JsonNode wrongPassword = grant(MOBILE_CLIENT_ID, EMAIL, "wrong", status().isBadRequest());
    assertThat(wrongPassword.get("error").asText()).isEqualTo("invalid_grant");

    loginAttempts.loginSucceeded(EMAIL);
    JsonNode rightPassword = grant(MOBILE_CLIENT_ID, EMAIL, PASSWORD, status().isBadRequest());
    assertThat(rightPassword.get("error").asText()).isEqualTo("email_not_verified");
  }

  @Test
  void fiveFailuresLockTheEmailEvenWithTheRightPassword() throws Exception {
    persistUser(true, true);

    for (int attempt = 0; attempt < 5; attempt++) {
      grant(MOBILE_CLIENT_ID, EMAIL, "wrong-password", status().isBadRequest());
    }

    JsonNode locked = grant(MOBILE_CLIENT_ID, EMAIL, PASSWORD, status().isBadRequest());
    assertThat(locked.get("error").asText()).isEqualTo("account_locked");
  }

  @Test
  void anUnregisteredEmailIsLockedTheSameWay() throws Exception {
    for (int attempt = 0; attempt < 5; attempt++) {
      grant(MOBILE_CLIENT_ID, "nobody@vanep.com", "wrong-password", status().isBadRequest());
    }

    JsonNode locked =
        grant(MOBILE_CLIENT_ID, "nobody@vanep.com", PASSWORD, status().isBadRequest());
    assertThat(locked.get("error").asText()).isEqualTo("account_locked");
  }

  @Test
  void theLockEndsOnceTheWindowHasPassed() throws Exception {
    persistUser(true, true);
    for (int attempt = 0; attempt < 5; attempt++) {
      grant(MOBILE_CLIENT_ID, EMAIL, "wrong-password", status().isBadRequest());
    }
    assertThat(loginAttempts.isBlocked(EMAIL)).isTrue();

    // The window is time based; clearing the counter is what expiry does to the bucket.
    loginAttempts.loginSucceeded(EMAIL);

    JsonNode tokens = grant(MOBILE_CLIENT_ID, EMAIL, PASSWORD, status().isOk());
    assertThat(tokens.hasNonNull("access_token")).isTrue();
  }

  /**
   * Design D2 authenticates the grant only for the mobile client id, so the web client never gets
   * far enough to hear {@code unauthorized_client}: it is rejected at client authentication.
   */
  @Test
  void theWebClientIsNotAllowedToUseTheGrant() throws Exception {
    persistUser(true, true);

    mockMvc
        .perform(
            post("/oauth2/token")
                .params(form(EMAIL, PASSWORD, "vanep-frontend"))
                .header("Accept", "application/json"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  @Test
  void accessTokenFromTheGrantIsAcceptedByTheApi() throws Exception {
    persistUser(true, true);
    JsonNode tokens = grant(MOBILE_CLIENT_ID, EMAIL, PASSWORD, status().isOk());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/user/me")
                .header("Authorization", "Bearer " + tokens.get("access_token").asText()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(EMAIL));
  }

  private JsonNode grant(String clientId, String username, String password, ResultMatcher expected)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/oauth2/token").params(form(username, password, clientId)))
            .andExpect(expected)
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static MultiValueMap<String, String> form(
      String username, String password, String clientId) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("grant_type", GRANT_TYPE);
    parameters.add("username", username);
    parameters.add("password", password);
    parameters.add("client_id", clientId);
    return parameters;
  }
}
