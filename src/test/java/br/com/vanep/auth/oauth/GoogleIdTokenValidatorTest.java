package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Rule 50: everything is signed and verified locally, so no test ever reaches Google's JWKS
 * endpoint.
 */
class GoogleIdTokenValidatorTest {

  private static final String WEB_CLIENT_ID = "web-client.apps.googleusercontent.com";
  private static final String ANDROID_CLIENT_ID = "android-client.apps.googleusercontent.com";

  private static RSAKey signingKey;
  private static RSAKey otherKey;

  @BeforeAll
  static void generateKeys() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
    otherKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
  }

  private GoogleIdTokenValidator validatorAccepting(List<String> audiences) throws Exception {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
    return new GoogleIdTokenValidator(decoder, audiences);
  }

  @Test
  void acceptsATokenSignedByGoogleForTheConfiguredAudience() throws Exception {
    GoogleIdentity identity =
        validatorAccepting(List.of(WEB_CLIENT_ID)).validate(idToken(claims().build()));

    assertThat(identity.subject()).isEqualTo("google-subject-1");
    assertThat(identity.email()).isEqualTo("person@gmail.com");
    assertThat(identity.name()).isEqualTo("Person Example");
  }

  @Test
  void acceptsAnAndroidTokenWhoseAzpIsTheAndroidClient() throws Exception {
    GoogleIdentity identity =
        validatorAccepting(List.of(WEB_CLIENT_ID))
            .validate(idToken(claims().claim("azp", ANDROID_CLIENT_ID).build()));

    assertThat(identity.subject()).isEqualTo("google-subject-1");
  }

  @Test
  void rejectsATokenSignedByAnotherKey() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    String token = idToken(claims().build(), otherKey);

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .extracting(failure -> ((OAuth2AuthenticationException) failure).getError().getErrorCode())
        .isEqualTo("invalid_grant");
  }

  @Test
  void rejectsAnIssuerThatIsNotGoogle() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    String token = idToken(claims().issuer("https://evil.example.com").build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsAnAudienceOutsideTheAllowedList() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    String token = idToken(claims().audience("someone-else.apps.googleusercontent.com").build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsAnAndroidAudienceWhenOnlyTheWebClientIsAllowed() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    String token = idToken(claims().audience(ANDROID_CLIENT_ID).build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsEveryTokenWhenTheAllowedListIsEmpty() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(""));
    String token = idToken(claims().build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsAnExpiredToken() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    Instant expired = Instant.now().minus(Duration.ofHours(2));
    String token =
        idToken(
            claims()
                .issueTime(Date.from(expired.minus(Duration.ofMinutes(5))))
                .expirationTime(Date.from(expired))
                .build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejectsAnUnverifiedGoogleEmail() throws Exception {
    GoogleIdTokenValidator validator = validatorAccepting(List.of(WEB_CLIENT_ID));
    String token = idToken(claims().claim("email_verified", false).build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  private static JWTClaimsSet.Builder claims() {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer("https://accounts.google.com")
        .subject("google-subject-1")
        .audience(WEB_CLIENT_ID)
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
        .claim("email", "person@gmail.com")
        .claim("email_verified", true)
        .claim("name", "Person Example");
  }

  private static String idToken(JWTClaimsSet claims) throws Exception {
    return idToken(claims, signingKey);
  }

  private static String idToken(JWTClaimsSet claims, RSAKey key) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(key.getKeyID())
                .build(),
            claims);
    jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
    return jwt.serialize();
  }
}
