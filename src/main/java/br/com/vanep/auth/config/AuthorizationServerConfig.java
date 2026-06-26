package br.com.vanep.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Configuração do Spring Authorization Server — equivalente ao Laravel Passport dos checklists.
 *
 * <p>Expõe os endpoints OAuth2 padrão ({@code /oauth2/authorize}, {@code /oauth2/token}, JWKS, OIDC
 * discovery) e registra o cliente público (PKCE) usado pelos frontends, no mesmo modelo
 * "authorization code + PKCE" que o checklists-frontend usa com o NextAuth.
 */
@Configuration
public class AuthorizationServerConfig {

  /**
   * Cliente público (sem secret, PKCE obrigatório) usado pelos frontends. Equivale ao client do
   * Passport com {@code token_endpoint_auth_method: none}.
   */
  @Bean
  public RegisteredClientRepository registeredClientRepository(
      @Value("${vanep.oauth.client.id}") String clientId,
      @Value("${vanep.oauth.client.redirect-uris}") List<String> redirectUris,
      @Value("${vanep.oauth.client.post-logout-redirect-uris:}")
          List<String> postLogoutRedirectUris,
      @Value("${vanep.oauth.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
      @Value("${vanep.oauth.refresh-token-ttl-days:90}") long refreshTokenTtlDays) {

    RegisteredClient.Builder builder =
        RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope("openid")
            .scope("profile")
            .scope("read")
            .scope("write")
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofMinutes(accessTokenTtlMinutes))
                    .refreshTokenTimeToLive(Duration.ofDays(refreshTokenTtlDays))
                    .reuseRefreshTokens(false)
                    .build());

    redirectUris.stream().filter(uri -> !uri.isBlank()).forEach(builder::redirectUri);
    postLogoutRedirectUris.stream()
        .filter(uri -> !uri.isBlank())
        .forEach(builder::postLogoutRedirectUri);

    return new InMemoryRegisteredClientRepository(builder.build());
  }

  /** Chave de assinatura (RSA) dos JWTs. Gerada em memória; ver README para uso em produção. */
  @Bean
  public JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsaKey();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().build();
  }

  private static RSAKey generateRsaKey() {
    KeyPair keyPair = newKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  private static KeyPair newKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception ex) {
      throw new IllegalStateException("Não foi possível gerar a chave RSA.", ex);
    }
  }
}
