package br.com.vanep.auth.config;

import br.com.vanep.auth.oauth.JwtTokenCustomizer;
import br.com.vanep.auth.oauth.grant.MobileAuthorizationGrantTypes;
import br.com.vanep.auth.oauth.grant.MobileRefreshTokenGenerator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Path;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@Configuration
public class AuthorizationServerConfig {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

  @Bean
  public RegisteredClientRepository registeredClientRepository(
      PasswordEncoder passwordEncoder,
      @Value("${vanep.oauth.client.id}") String clientId,
      @Value("${vanep.oauth.client.secret:}") String clientSecret,
      @Value("${vanep.oauth.client.redirect-uris}") List<String> redirectUris,
      @Value("${vanep.oauth.client.post-logout-redirect-uris:}")
          List<String> postLogoutRedirectUris,
      @Value("${vanep.oauth.mobile-client.id:vanep-mobile}") String mobileClientId,
      @Value("${vanep.oauth.mobile-client.redirect-uris:}") List<String> mobileRedirectUris,
      @Value("${vanep.oauth.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
      @Value("${vanep.oauth.refresh-token-ttl-days:90}") long refreshTokenTtlDays) {

    if (clientSecret.isBlank()) {
      throw new IllegalStateException(
          "Configure vanep.oauth.client.secret (VANEP_OAUTH_CLIENT_SECRET). The web client must"
              + " authenticate with a secret, otherwise the authorization server refuses it a"
              + " refresh token and every browser session dies at the access token TTL.");
    }

    ClientSettings clientSettings =
        ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build();

    // Rotation stays off for the web client: the Next.js BFF keeps the session in a stateless
    // cookie shared by concurrent serverless invocations, so it cannot store a rotated token
    // safely — every parallel request would burn the same one and all but the winner would get
    // invalid_grant. The app owns a single mutable store, so it keeps rotation.
    RegisteredClient.Builder webBuilder =
        buildConfidentialClient(
            clientId,
            passwordEncoder.encode(clientSecret),
            clientSettings,
            tokenSettings(accessTokenTtlMinutes, refreshTokenTtlDays, true));
    applyRedirectUris(webBuilder, redirectUris);
    applyPostLogoutRedirectUris(webBuilder, postLogoutRedirectUris);
    RegisteredClient webClient = webBuilder.build();

    // Cliente público do app mobile: mesmo fluxo Authorization Code + PKCE, mas com
    // redirect custom-scheme (ex.: com.vanep.vanep_mobile://oauth2redirect) capturado no WebView.
    RegisteredClient.Builder mobileBuilder =
        buildPublicClient(
            mobileClientId,
            clientSettings,
            tokenSettings(accessTokenTtlMinutes, refreshTokenTtlDays, false));
    applyRedirectUris(mobileBuilder, mobileRedirectUris);
    // Only the native app gets the extension grants. This is configuration hygiene, not a
    // security barrier: the defence is the uniform lockout plus the rate limit.
    mobileBuilder.authorizationGrantType(MobileAuthorizationGrantTypes.PASSWORD);
    mobileBuilder.authorizationGrantType(MobileAuthorizationGrantTypes.GOOGLE);
    RegisteredClient mobileClient = mobileBuilder.build();

    return new InMemoryRegisteredClientRepository(webClient, mobileClient);
  }

  private static TokenSettings tokenSettings(
      long accessTokenTtlMinutes, long refreshTokenTtlDays, boolean reuseRefreshTokens) {
    return TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofMinutes(accessTokenTtlMinutes))
        .refreshTokenTimeToLive(Duration.ofDays(refreshTokenTtlDays))
        .reuseRefreshTokens(reuseRefreshTokens)
        .build();
  }

  private static RegisteredClient.Builder buildClient(
      String clientId, ClientSettings clientSettings, TokenSettings tokenSettings) {
    return RegisteredClient.withId(UUID.nameUUIDFromBytes(clientId.getBytes()).toString())
        .clientId(clientId)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .scope("openid")
        .scope("profile")
        .scope("read")
        .scope("write")
        .clientSettings(clientSettings)
        .tokenSettings(tokenSettings);
  }

  private static RegisteredClient.Builder buildPublicClient(
      String clientId, ClientSettings clientSettings, TokenSettings tokenSettings) {
    return buildClient(clientId, clientSettings, tokenSettings)
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
  }

  /**
   * The Next.js BFF runs server-side, so it can hold a secret — and it has to. {@code
   * OAuth2RefreshTokenGenerator} refuses a refresh token to any public client on {@code
   * authorization_code}, which left every browser session expiring at the access token TTL with no
   * way back. Authenticating with a secret also keeps {@link
   * br.com.vanep.auth.oauth.grant.MobileClientAuthenticationConverter} from claiming the web
   * client's token requests, since it only matches a bare {@code client_id}.
   */
  private static RegisteredClient.Builder buildConfidentialClient(
      String clientId,
      String encodedSecret,
      ClientSettings clientSettings,
      TokenSettings tokenSettings) {
    return buildClient(clientId, clientSettings, tokenSettings)
        .clientSecret(encodedSecret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
  }

  private static RegisteredClient.Builder applyRedirectUris(
      RegisteredClient.Builder builder, List<String> redirectUris) {
    redirectUris.stream().filter(uri -> !uri.isBlank()).forEach(builder::redirectUri);
    return builder;
  }

  private static RegisteredClient.Builder applyPostLogoutRedirectUris(
      RegisteredClient.Builder builder, List<String> postLogoutRedirectUris) {
    postLogoutRedirectUris.stream()
        .filter(uri -> !uri.isBlank())
        .forEach(builder::postLogoutRedirectUri);
    return builder;
  }

  @Bean
  @Profile("docker | prod | local")
  public OAuth2AuthorizationService authorizationService(
      JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  }

  /**
   * Declared as a bean so the mobile grants and the server's own endpoints share one store; without
   * it each side would build its own in-memory instance and refresh would not find the token.
   */
  @Bean
  @Profile("!docker & !prod & !local")
  public OAuth2AuthorizationService inMemoryAuthorizationService() {
    return new InMemoryOAuth2AuthorizationService();
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource(
      @Value("${vanep.oauth.jwk.private-key:}") String privateKeyPem,
      @Value("${vanep.oauth.jwk.public-key:}") String publicKeyPem,
      @Value("${vanep.oauth.jwk.key-id:vanep-rsa-key}") String keyId,
      @Value("${vanep.oauth.jwk.dev-key-path:.dev/oauth-jwk.json}") String devKeyPath,
      Environment environment) {
    RSAKey rsaKey;
    if (!privateKeyPem.isBlank() && !publicKeyPem.isBlank()) {
      rsaKey = RsaKeys.fromPem(privateKeyPem, publicKeyPem, keyId);
      log.info("JWK RSA carregada da configuração (kid={}).", keyId);
    } else if (List.of(environment.getActiveProfiles()).contains("prod")) {
      throw new IllegalStateException(
          "Em produção configure vanep.oauth.jwk.private-key e vanep.oauth.jwk.public-key (PEM).");
    } else {
      // Dev: persiste a chave em disco para que os tokens sobrevivam a restart.
      // Configure vanep.oauth.jwk.* (PEM) em produção / múltiplas instâncias.
      rsaKey = RsaKeys.loadOrCreate(keyId, Path.of(devKeyPath));
      log.info(
          "JWK RSA de desenvolvimento persistida em {} (kid={}).", devKeyPath, rsaKey.getKeyID());
    }
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
  }

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

  /**
   * Declaring this bean takes over from the server's default generator, so the {@link
   * JwtTokenCustomizer} has to be wired by hand here — without it every access token would ship
   * without {@code uid}, {@code roles} and {@code permissions}.
   */
  @Bean
  public OAuth2TokenGenerator<?> tokenGenerator(
      JWKSource<SecurityContext> jwkSource,
      JwtTokenCustomizer jwtTokenCustomizer,
      @Value("${vanep.oauth.mobile-client.id:vanep-mobile}") String mobileClientId) {
    JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
    jwtGenerator.setJwtCustomizer(jwtTokenCustomizer);
    return new DelegatingOAuth2TokenGenerator(
        jwtGenerator,
        new OAuth2AccessTokenGenerator(),
        new MobileRefreshTokenGenerator(mobileClientId));
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
