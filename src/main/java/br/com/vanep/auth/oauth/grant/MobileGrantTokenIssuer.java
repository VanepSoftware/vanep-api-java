package br.com.vanep.auth.oauth.grant;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Turns an authenticated account into the same tokens and the same stored authorization the
 * built-in grants produce, so refresh, revoke and introspection keep working unchanged.
 */
public final class MobileGrantTokenIssuer {

  private final OAuth2AuthorizationService authorizations;
  private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

  public MobileGrantTokenIssuer(
      OAuth2AuthorizationService authorizations,
      OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
    this.authorizations = authorizations;
    this.tokenGenerator = tokenGenerator;
  }

  public OAuth2AccessTokenAuthenticationToken issue(
      OAuth2ClientAuthenticationToken clientPrincipal,
      Authentication userPrincipal,
      AuthorizationGrantType grantType,
      Authentication authorizationGrant) {

    RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
    Set<String> authorizedScopes = apiScopesOf(registeredClient);

    DefaultOAuth2TokenContext.Builder contextBuilder =
        DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(userPrincipal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizedScopes(authorizedScopes)
            .authorizationGrantType(grantType)
            .authorizationGrant(authorizationGrant);

    OAuth2Authorization.Builder authorizationBuilder =
        OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(userPrincipal.getName())
            .authorizationGrantType(grantType)
            .authorizedScopes(authorizedScopes)
            // The refresh grant rebuilds the token context from this attribute.
            .attribute(Principal.class.getName(), userPrincipal);

    OAuth2TokenContext accessTokenContext =
        contextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
    OAuth2Token generatedAccessToken = generate(accessTokenContext, "access token");
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            generatedAccessToken.getTokenValue(),
            generatedAccessToken.getIssuedAt(),
            generatedAccessToken.getExpiresAt(),
            authorizedScopes);
    if (generatedAccessToken instanceof ClaimAccessor claims) {
      authorizationBuilder.token(
          accessToken,
          metadata ->
              metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()));
    } else {
      authorizationBuilder.accessToken(accessToken);
    }

    OAuth2TokenContext refreshTokenContext =
        contextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
    OAuth2RefreshToken refreshToken = null;
    if (tokenGenerator.generate(refreshTokenContext) instanceof OAuth2RefreshToken generated) {
      refreshToken = generated;
      authorizationBuilder.refreshToken(refreshToken);
    }

    authorizations.save(authorizationBuilder.build());

    return new OAuth2AccessTokenAuthenticationToken(
        registeredClient, clientPrincipal, accessToken, refreshToken);
  }

  private OAuth2Token generate(OAuth2TokenContext context, String tokenName) {
    OAuth2Token token = tokenGenerator.generate(context);
    if (token == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.SERVER_ERROR,
              "The token generator failed to generate the " + tokenName + ".",
              null));
    }
    return token;
  }

  /**
   * The mobile grants never mint an ID token, so {@code openid} would promise what we do not do.
   */
  private static Set<String> apiScopesOf(RegisteredClient registeredClient) {
    Set<String> scopes = new LinkedHashSet<>(registeredClient.getScopes());
    scopes.remove(OidcScopes.OPENID);
    return scopes;
  }
}
