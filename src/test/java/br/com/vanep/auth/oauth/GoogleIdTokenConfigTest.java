package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GoogleIdTokenConfigTest {

  private static final String EMAIL = "google-config@vanep.com";

  @Autowired private ApplicationContext applicationContext;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private UserRepository users;
  @Autowired private JWKSource<SecurityContext> jwkSource;
  @Autowired private GoogleIdTokenValidator googleIdTokenValidator;

  @Value("${vanep.google.id-token.audiences}")
  private String configuredAudiences;

  @Value("${GOOGLE_CLIENT_ID:}")
  private String googleClientId;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void theGoogleDecoderIsNotASecondJwtDecoderBean() {
    assertThat(applicationContext.getBeanNamesForType(JwtDecoder.class)).hasSize(1);
    assertThat(googleIdTokenValidator).isNotNull();
  }

  @Test
  void vanepIssuedTokensAreStillAcceptedOnTheApi() throws Exception {
    UserModel user = persistUser();

    mockMvc
        .perform(
            get("/api/user/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + vanepToken(user)))
        .andExpect(status().isOk());
  }

  @Test
  void allowedAudiencesFallBackToTheGoogleWebClientId() {
    assertThat(googleClientId).isNotBlank();
    assertThat(configuredAudiences).isEqualTo(googleClientId);
  }

  private String vanepToken(UserModel user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getEmail())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(15)))
            .claims(
                existing ->
                    existing.putAll(
                        Map.of("uid", user.getToken(), "roles", List.of("ROLE_CLIENT"))))
            .build();
    return new NimbusJwtEncoder(jwkSource)
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }

  private UserModel persistUser() {
    UserModel user = new UserModel();
    user.setType(UserType.CLIENT);
    user.setName("Google Config");
    user.setEmail(EMAIL);
    user.setDocument("39053344705");
    user.setPassword("hashed");
    user.setVerified(true);
    return users.save(user);
  }
}
