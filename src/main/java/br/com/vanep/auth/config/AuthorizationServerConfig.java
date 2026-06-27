package br.com.vanep.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
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
 *
 * <p>Persistência (produção): a chave de assinatura (JWK) vem da config ({@code vanep.oauth.jwk.*})
 * e o estado de autorização (codes/refresh tokens) é gravado no banco ({@link
 * JdbcOAuth2AuthorizationService}). O repositório de clientes continua em memória por ser
 * determinístico a partir da config (idêntico em todas as instâncias).
 */
@Configuration
public class AuthorizationServerConfig {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

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
        RegisteredClient.withId(UUID.nameUUIDFromBytes(clientId.getBytes()).toString())
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

  /**
   * Persiste codes/tokens no banco (tabela {@code oauth2_authorization}). Fora do profile de teste,
   * onde o schema vem só das entidades JPA e o store em memória padrão é suficiente.
   */
  @Bean
  @Profile("!test")
  public OAuth2AuthorizationService authorizationService(
      JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  }

  /**
   * Chave de assinatura (RSA) dos JWTs. Em produção é obrigatório configurar {@code
   * vanep.oauth.jwk.private-key}/{@code public-key} (PEM) para estabilidade entre restarts e
   * instâncias. Em dev, na ausência da config, uma chave efêmera é gerada (com aviso).
   */
  @Bean
  public JWKSource<SecurityContext> jwkSource(
      @Value("${vanep.oauth.jwk.private-key:}") String privateKeyPem,
      @Value("${vanep.oauth.jwk.public-key:}") String publicKeyPem,
      @Value("${vanep.oauth.jwk.key-id:vanep-rsa-key}") String keyId,
      Environment environment) {
    RSAKey rsaKey;
    if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
      rsaKey = RsaKeys.fromPem(privateKeyPem, publicKeyPem, keyId);
      log.info("JWK RSA carregada da configuração (kid={}).", keyId);
    } else {
      if (List.of(environment.getActiveProfiles()).contains("prod")) {
        throw new IllegalStateException(
            "Em produção configure vanep.oauth.jwk.private-key e vanep.oauth.jwk.public-key (PEM).");
      }
      log.warn(
          "JWK RSA efêmera gerada em memória (apenas dev): tokens não sobrevivem a restart nem "
              + "funcionam em múltiplas instâncias. Configure vanep.oauth.jwk.* em produção.");
      rsaKey = RsaKeys.generate(keyId);
    }
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

  /**
   * Decoder usado pelo Resource Server. Quando {@code vanep.oauth.issuer} está definido, valida
   * também o {@code iss} do token (além de assinatura e expiração).
   */
  @Bean
  public JwtDecoder jwtDecoder(
      JWKSource<SecurityContext> jwkSource, @Value("${vanep.oauth.issuer:}") String issuer) {
    NimbusJwtDecoder decoder =
        (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    if (!issuer.isBlank()) {
      decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    }
    return decoder;
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings(
      @Value("${vanep.oauth.issuer:}") String issuer) {
    AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
    if (!issuer.isBlank()) {
      builder.issuer(issuer);
    }
    return builder.build();
  }
}
