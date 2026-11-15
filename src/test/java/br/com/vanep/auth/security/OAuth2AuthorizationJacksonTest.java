package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class OAuth2AuthorizationJacksonTest {

  private static final String REFRESH_TOKEN_VALUE = "refresh-value";

  private EmbeddedDatabase database;
  private RegisteredClient client;
  private JdbcOAuth2AuthorizationService store;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2)
            .addScript(
                "org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql")
            .build();
    client =
        RegisteredClient.withId("client-1")
            .clientId("vanep-frontend")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .redirectUri("http://localhost:3000/callback")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    InMemoryRegisteredClientRepository clients = new InMemoryRegisteredClientRepository(client);
    store = new JdbcOAuth2AuthorizationService(new JdbcTemplate(database), clients);
    OAuth2AuthorizationJackson.install(store, clients);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  void jdbcStoreReadsTheFormLoginPrincipalBack() {
    VanepUserDetails user =
        new VanepUserDetails(
            "a@vanep.com",
            "bound$hash",
            "user-token-alpha",
            true,
            true,
            List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            user, user.getPassword(), user.getAuthorities());
    Instant now = Instant.now();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-value",
            now,
            now.plusSeconds(900),
            Set.of("read"));
    store.save(
        OAuth2Authorization.withRegisteredClient(client)
            .principalName(user.getUsername())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .attribute(Principal.class.getName(), authentication)
            .token(accessToken)
            .refreshToken(new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, now, now.plusSeconds(3600)))
            .build());

    OAuth2Authorization found =
        Objects.requireNonNull(
            store.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN));
    UsernamePasswordAuthenticationToken stored = found.getAttribute(Principal.class.getName());
    VanepUserDetails principal = (VanepUserDetails) stored.getPrincipal();

    assertThat(principal.getUsername()).isEqualTo("a@vanep.com");
    assertThat(principal.getUserToken()).isEqualTo("user-token-alpha");
    assertThat(principal.getPassword()).isEqualTo("bound$hash");
    assertThat(principal.isEnabled()).isTrue();
    assertThat(principal.isAccountNonLocked()).isTrue();
    assertThat(principal.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactly("ROLE_CLIENT");
  }
}
