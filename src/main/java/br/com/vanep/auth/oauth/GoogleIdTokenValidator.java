package br.com.vanep.auth.oauth;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Validates the ID token the native Google SDK hands to the app.
 *
 * <p>The decoder stays private on purpose: the resource server resolves the Vanep decoder with
 * {@code getBean(JwtDecoder.class)}, so exposing a second bean of that type breaks startup, even
 * behind a qualifier.
 */
@Component
public class GoogleIdTokenValidator {

  private static final Set<String> ISSUERS =
      Set.of("accounts.google.com", "https://accounts.google.com");

  private final NimbusJwtDecoder decoder;
  private final List<String> audiences;

  @Autowired
  public GoogleIdTokenValidator(
      @Value("${vanep.google.id-token.jwks-uri}") String jwkSetUri,
      @Value("${vanep.google.id-token.audiences:}") List<String> audiences) {
    this(NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build(), audiences);
  }

  GoogleIdTokenValidator(NimbusJwtDecoder decoder, List<String> audiences) {
    this.audiences = audiences.stream().filter(audience -> !audience.isBlank()).toList();
    this.decoder = decoder;
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), claimValidator()));
  }

  public GoogleIdentity validate(String idToken) {
    try {
      Jwt jwt = decoder.decode(idToken);
      return new GoogleIdentity(
          jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
    } catch (JwtException ex) {
      throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT), ex);
    }
  }

  private OAuth2TokenValidator<Jwt> claimValidator() {
    return jwt -> {
      if (!ISSUERS.contains(jwt.getClaimAsString("iss"))) {
        return failure("The ID token was not issued by Google.");
      }
      if (audiences.stream().noneMatch(jwt.getAudience()::contains)) {
        return failure("The ID token audience is not allowed.");
      }
      if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
        return failure("The Google account email is not verified.");
      }
      return OAuth2TokenValidatorResult.success();
    };
  }

  private static OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, description, null));
  }
}
