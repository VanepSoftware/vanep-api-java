package br.com.vanep.auth.oauth.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.oauth.GoogleIdTokenValidator;
import br.com.vanep.auth.oauth.GoogleIdentity;
import br.com.vanep.auth.signup.SignupTicketRepository;
import br.com.vanep.user.enums.AuthProvider;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.OAuthAccountModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.OAuthAccountRepository;
import br.com.vanep.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;

/**
 * Rule 50: the ID token validator is stubbed, so nothing here reaches Google. Its own rules are
 * covered by {@code GoogleIdTokenValidatorTest} with a locally signed token.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MobileGoogleGrantTest {

  private static final String GRANT_TYPE = "urn:vanep:params:oauth:grant-type:google";
  private static final String MOBILE_CLIENT_ID = "vanep-mobile";
  private static final String ID_TOKEN = "id-token-from-the-native-sdk";
  private static final String SUBJECT = "google-subject-1";
  private static final String EMAIL = "person@gmail.com";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private OAuthAccountRepository oauthAccounts;
  @Autowired private SignupTicketRepository signupTickets;
  @MockitoBean private GoogleIdTokenValidator idTokenValidator;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  private void googleReturns(GoogleIdentity identity) {
    when(idTokenValidator.validate(ID_TOKEN)).thenReturn(identity);
  }

  @Test
  void aLinkedAccountGetsTokens() throws Exception {
    UserModel user = persistUser(EMAIL);
    linkGoogle(user);
    googleReturns(new GoogleIdentity(SUBJECT, EMAIL, "Person"));

    JsonNode tokens = grant(MOBILE_CLIENT_ID, status().isOk());

    assertThat(tokens.hasNonNull("access_token")).isTrue();
    assertThat(tokens.hasNonNull("refresh_token")).isTrue();
  }

  @Test
  void anExistingEmailIsLinkedAndGetsTokens() throws Exception {
    persistUser(EMAIL);
    googleReturns(new GoogleIdentity(SUBJECT, EMAIL, "Person"));

    JsonNode tokens = grant(MOBILE_CLIENT_ID, status().isOk());

    assertThat(tokens.hasNonNull("access_token")).isTrue();
    assertThat(oauthAccounts.findByProviderAndProviderUid(AuthProvider.GOOGLE, SUBJECT))
        .isPresent();
  }

  @Test
  void aDeactivatedAccountIsRejected() throws Exception {
    UserModel user = persistUser(EMAIL);
    linkGoogle(user);
    users.delete(user);
    googleReturns(new GoogleIdentity(SUBJECT, EMAIL, "Person"));

    JsonNode error = grant(MOBILE_CLIENT_ID, status().isBadRequest());

    assertThat(error.get("error").asText()).isEqualTo("account_disabled");
  }

  @Test
  void aBrandNewPersonGetsASignupTicket() throws Exception {
    googleReturns(new GoogleIdentity(SUBJECT, EMAIL, "Person Example"));

    JsonNode error = grant(MOBILE_CLIENT_ID, status().isBadRequest());

    assertThat(error.get("error").asText()).isEqualTo("registration_required");
    assertThat(error.get("signup_ticket").asText()).isNotBlank();
    assertThat(error.get("email").asText()).isEqualTo(EMAIL);
    assertThat(error.get("name").asText()).isEqualTo("Person Example");
    assertThat(error.has("access_token")).isFalse();
    assertThat(signupTickets.count()).isEqualTo(1);
    assertThat(users.count()).isZero();
  }

  @Test
  void anInvalidIdTokenIsRejected() throws Exception {
    when(idTokenValidator.validate(anyString()))
        .thenThrow(
            new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT)));

    JsonNode error = grant(MOBILE_CLIENT_ID, status().isBadRequest());

    assertThat(error.get("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void theWebClientCannotUseTheGoogleGrant() throws Exception {
    mockMvc
        .perform(post("/oauth2/token").params(form("vanep-frontend")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  private JsonNode grant(String clientId, ResultMatcher expected) throws Exception {
    MvcResult result =
        mockMvc
            .perform(post("/oauth2/token").params(form(clientId)))
            .andExpect(expected)
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static MultiValueMap<String, String> form(String clientId) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("grant_type", GRANT_TYPE);
    parameters.add("id_token", ID_TOKEN);
    parameters.add("client_id", clientId);
    return parameters;
  }

  private UserModel persistUser(String email) {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Person");
    user.setEmail(email);
    user.setDocument("39053344705");
    user.setVerified(true);
    return users.save(user);
  }

  private void linkGoogle(UserModel user) {
    OAuthAccountModel account = new OAuthAccountModel();
    account.setUser(user);
    account.setProvider(AuthProvider.GOOGLE);
    account.setProviderUid(SUBJECT);
    account.setEmail(user.getEmail());
    oauthAccounts.save(account);
  }
}
