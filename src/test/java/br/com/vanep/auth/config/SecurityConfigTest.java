package br.com.vanep.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigTest {

  private final JwtAuthenticationConverter converter =
      new SecurityConfig().jwtAuthenticationConverter();

  private Jwt jwtWithClaims(Map<String, Object> claims) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claims(map -> map.putAll(claims))
        .build();
  }

  @Test
  void permissionsClaimBecomesAuthoritiesWithoutPrefix() {
    Jwt jwt =
        jwtWithClaims(
            Map.of("sub", "user@vanep.com", "permissions", List.of("list_roles", "delete_role")));

    var authorities = converter.convert(jwt).getAuthorities();

    assertThat(authorities.stream().map(GrantedAuthority::getAuthority))
        .contains("list_roles", "delete_role")
        .noneMatch(
            authority -> authority.startsWith("ROLE_list") || authority.startsWith("ROLE_delete"));
  }

  @Test
  void rolesAndPermissionsClaimsBothBecomeAuthorities() {
    Jwt jwt =
        jwtWithClaims(
            Map.of(
                "sub", "user@vanep.com",
                "roles", List.of("ROLE_ADMIN"),
                "permissions", List.of("list_roles")));

    var authorities = converter.convert(jwt).getAuthorities();

    assertThat(authorities.stream().map(GrantedAuthority::getAuthority))
        .contains("ROLE_ADMIN", "list_roles");
  }
}
