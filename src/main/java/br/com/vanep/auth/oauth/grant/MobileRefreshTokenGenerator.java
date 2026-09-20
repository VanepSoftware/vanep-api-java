package br.com.vanep.auth.oauth.grant;

import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Issues refresh tokens to the mobile client in every grant. The standard generator refuses them to
 * any public client on {@code authorization_code}, which would leave the native app re-asking for
 * credentials every time the access token expires.
 */
public final class MobileRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

  private final StringKeyGenerator refreshTokenGenerator =
      new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);
  private final OAuth2RefreshTokenGenerator delegate = new OAuth2RefreshTokenGenerator();
  private final String mobileClientId;

  public MobileRefreshTokenGenerator(String mobileClientId) {
    this.mobileClientId = mobileClientId;
  }

  @Override
  public OAuth2RefreshToken generate(OAuth2TokenContext context) {
    if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
      return null;
    }
    RegisteredClient registeredClient = context.getRegisteredClient();
    if (!mobileClientId.equals(registeredClient.getClientId())) {
      return delegate.generate(context);
    }
    if (!registeredClient
        .getAuthorizationGrantTypes()
        .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
      return null;
    }
    Instant issuedAt = Instant.now();
    Instant expiresAt =
        issuedAt.plus(registeredClient.getTokenSettings().getRefreshTokenTimeToLive());
    return new OAuth2RefreshToken(refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
  }
}
