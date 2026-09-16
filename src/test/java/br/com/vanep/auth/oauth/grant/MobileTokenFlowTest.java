package br.com.vanep.auth.oauth.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MobileTokenFlowTest {

  private static final String MOBILE_CLIENT_ID = "vanep-mobile";
  private static final String WEB_CLIENT_ID = "vanep-frontend";
  private static final String MOBILE_REDIRECT_URI = "com.vanep.vanepmobile://oauth2redirect";
  private static final String WEB_REDIRECT_URI = "http://localhost:3000/api/auth/callback/vanep";
  private static final String CODE_VERIFIER = "vanep-mobile-code-verifier-with-enough-entropy";
  private static final String DRIVER_EMAIL = "mobile-driver@vanep.com";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtDecoder jwtDecoder;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    persistDriver();
  }

  @Test
  void mobileAuthorizationCodeNowReturnsARefreshToken() throws Exception {
    JsonNode tokens = exchangeAuthorizationCode(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI);

    assertThat(tokens.hasNonNull("access_token")).isTrue();
    assertThat(tokens.hasNonNull("refresh_token")).isTrue();
    assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
  }

  @Test
  void webClientKeepsGettingNoRefreshToken() throws Exception {
    JsonNode tokens = exchangeAuthorizationCode(WEB_CLIENT_ID, WEB_REDIRECT_URI);

    assertThat(tokens.hasNonNull("access_token")).isTrue();
    assertThat(tokens.has("refresh_token")).isFalse();
  }

  @Test
  void accessTokenKeepsItsVanepClaims() throws Exception {
    JsonNode tokens = exchangeAuthorizationCode(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI);

    var claims = jwtDecoder.decode(tokens.get("access_token").asText()).getClaims();

    UserModel driver = users.findByEmail(DRIVER_EMAIL).orElseThrow();
    assertThat(claims).containsEntry("uid", driver.getToken());
    assertThat(claims).containsEntry("user_type", "DRIVER");
    assertThat(claims).containsEntry("driver_status", DriverApprovalStatus.PENDING.name());
    assertThat(claims.get("roles")).asInstanceOf(LIST).containsExactly("ROLE_DRIVER");
    assertThat(claims).containsKey("permissions");
  }

  @Test
  void refreshWithOnlyTheClientIdRotatesBothTokens() throws Exception {
    String firstRefreshToken =
        exchangeAuthorizationCode(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI)
            .get("refresh_token")
            .asText();

    JsonNode refreshed = refresh(MOBILE_CLIENT_ID, firstRefreshToken, status().isOk());

    assertThat(refreshed.hasNonNull("access_token")).isTrue();
    assertThat(refreshed.get("refresh_token").asText()).isNotEqualTo(firstRefreshToken);
  }

  @Test
  void rotatedRefreshTokenCannotBeReused() throws Exception {
    String firstRefreshToken =
        exchangeAuthorizationCode(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI)
            .get("refresh_token")
            .asText();
    refresh(MOBILE_CLIENT_ID, firstRefreshToken, status().isOk());

    JsonNode error = refresh(MOBILE_CLIENT_ID, firstRefreshToken, status().isBadRequest());

    assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void revokeWithOnlyTheClientIdInvalidatesTheRefreshToken() throws Exception {
    String refreshToken =
        exchangeAuthorizationCode(MOBILE_CLIENT_ID, MOBILE_REDIRECT_URI)
            .get("refresh_token")
            .asText();

    mockMvc
        .perform(
            post("/oauth2/revoke")
                .params(form("token", refreshToken, "client_id", MOBILE_CLIENT_ID)))
        .andExpect(status().isOk());

    JsonNode error = refresh(MOBILE_CLIENT_ID, refreshToken, status().isBadRequest());
    assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void revokeFromAnUnknownClientIsRejectedAsInvalidClient() throws Exception {
    mockMvc
        .perform(
            post("/oauth2/revoke").params(form("token", "whatever", "client_id", "unknown-app")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  @Test
  void refreshFromAnUnknownClientIsRejectedAsInvalidClient() throws Exception {
    mockMvc
        .perform(
            post("/oauth2/token")
                .params(
                    form(
                        "grant_type",
                        "refresh_token",
                        "refresh_token",
                        "whatever",
                        "client_id",
                        "unknown-app")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  private JsonNode refresh(
      String clientId,
      String refreshToken,
      org.springframework.test.web.servlet.ResultMatcher expectedStatus)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .params(
                        form(
                            "grant_type",
                            "refresh_token",
                            "refresh_token",
                            refreshToken,
                            "client_id",
                            clientId)))
            .andExpect(expectedStatus)
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode exchangeAuthorizationCode(String clientId, String redirectUri) throws Exception {
    String code = authorizationCodeFor(clientId, redirectUri);
    MvcResult result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .params(
                        form(
                            "grant_type",
                            "authorization_code",
                            "code",
                            code,
                            "redirect_uri",
                            redirectUri,
                            "client_id",
                            clientId,
                            "code_verifier",
                            CODE_VERIFIER)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String authorizationCodeFor(String clientId, String redirectUri) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("scope", "read write")
                    .queryParam("code_challenge", codeChallenge())
                    .queryParam("code_challenge_method", "S256")
                    .with(user(DRIVER_EMAIL).roles("DRIVER")))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String location = result.getResponse().getRedirectedUrl();
    return UriComponentsBuilder.fromUri(URI.create(location))
        .build()
        .getQueryParams()
        .getFirst("code");
  }

  private static String codeChallenge() throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(CODE_VERIFIER.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private static MultiValueMap<String, String> form(String... keysAndValues) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      parameters.add(keysAndValues[index], keysAndValues[index + 1]);
    }
    return parameters;
  }

  private void persistDriver() {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Mobile Driver");
    user.setEmail(DRIVER_EMAIL);
    user.setDocument("52998224725");
    user.setPassword(passwordEncoder.encode("secret1"));
    user.setVerified(true);
    UserModel saved = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(saved);
    driver.setBasePrice(new BigDecimal("120.00"));
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    drivers.save(driver);
  }
}
