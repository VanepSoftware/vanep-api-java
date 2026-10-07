package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

@ExtendWith(MockitoExtension.class)
class JwtClaimsAuthorizationStoreTest {
  private static final String PRINCIPAL = "a@vanep.com";
  private static final String REFRESH_TOKEN_VALUE = "refresh-value";

  @Mock private UserRepository users;
  @Mock private DriverRepository drivers;
  @Mock private AssistantRepository assistants;
  @Mock private RoleRepository roles;

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
            .clientId("vanep-mobile")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();
    store =
        new JdbcOAuth2AuthorizationService(
            new JdbcTemplate(database), new InMemoryRegisteredClientRepository(client));
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  private UserModel clientUser(Long roleId) {
    UserModel user = new UserModel();
    user.setId(1L);
    user.setRoleId(roleId);
    user.setType(UserType.CLIENT);
    user.setEmail(PRINCIPAL);
    user.setToken("tok-1");
    when(users.findByEmail(PRINCIPAL)).thenReturn(Optional.of(user));
    return user;
  }

  private Map<String, Object> claimsEmittedFor(UserModel user) {
    JwtEncodingContext context =
        JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder().subject(user.getEmail()))
            .principal(new TestingAuthenticationToken(user.getEmail(), null))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();
    new JwtTokenCustomizer(users, drivers, assistants, roles).customize(context);
    return context.getClaims().build().getClaims();
  }

  private void saveAuthorizationCarrying(Map<String, Object> claims) {
    Instant now = Instant.now();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-value",
            now,
            now.plusSeconds(900),
            Set.of("read"));
    OAuth2Authorization authorization =
        OAuth2Authorization.withRegisteredClient(client)
            .principalName(PRINCIPAL)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .token(
                accessToken,
                metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims))
            .refreshToken(new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, now, now.plusSeconds(3600)))
            .build();
    store.save(authorization);
  }

  private OAuth2Authorization findByRefreshToken() {
    return Objects.requireNonNull(
        store.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN),
        "the stored authorization must be found by its refresh token");
  }

  @Test
  void refreshLookupSurvivesTheRoleAndPermissionClaims() {
    UserModel user = clientUser(10L);
    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setPermissions(List.of("list_roles", "show_role", "delete_role"));
    RoleModel role = new RoleModel();
    role.setRolePermission(bundle);
    when(roles.findById(10L)).thenReturn(Optional.of(role));
    saveAuthorizationCarrying(claimsEmittedFor(user));

    OAuth2Authorization found = findByRefreshToken();

    Map<String, Object> claims = found.getAccessToken().getClaims();
    assertThat(claims)
        .containsEntry("permissions", List.of("list_roles", "show_role", "delete_role"))
        .containsEntry("roles", List.of("ROLE_CLIENT"));
  }

  @Test
  void refreshLookupSurvivesAUserWithoutRole() {
    UserModel user = clientUser(null);
    saveAuthorizationCarrying(claimsEmittedFor(user));

    OAuth2Authorization found = findByRefreshToken();

    assertThat(found.getAccessToken().getClaims())
        .containsEntry("permissions", List.of())
        .containsEntry("roles", List.of("ROLE_CLIENT"));
  }
}
