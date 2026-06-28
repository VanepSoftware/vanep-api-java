package br.com.vanep.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.Driver;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

@ExtendWith(MockitoExtension.class)
class JwtTokenCustomizerTest {

  @Mock private UserRepository users;
  @Mock private DriverRepository drivers;

  private JwtEncodingContext context(OAuth2TokenType type, String principal) {
    return JwtEncodingContext.with(
            JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder().subject(principal))
        .principal(new TestingAuthenticationToken(principal, null))
        .tokenType(type)
        .build();
  }

  @Test
  void addsRoleAndIdentityClaimsForClient() {
    User user = new User();
    user.setId(1L);
    user.setType(UserType.CLIENT);
    user.setEmail("a@vanep.com");
    user.setToken("tok-1");
    when(users.findByEmailAndDeletedAtIsNull("a@vanep.com")).thenReturn(Optional.of(user));

    JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, "a@vanep.com");
    new JwtTokenCustomizer(users, drivers).customize(ctx);
    JwtClaimsSet claims = ctx.getClaims().build();

    String userType = claims.getClaim("user_type");
    String uid = claims.getClaim("uid");
    List<String> roles = claims.getClaim("roles");
    assertThat(userType).isEqualTo("CLIENT");
    assertThat(uid).isEqualTo("tok-1");
    assertThat(roles).containsExactly("ROLE_CLIENT");
  }

  @Test
  void addsDriverStatusForDriver() {
    User user = new User();
    user.setId(2L);
    user.setType(UserType.DRIVER);
    user.setEmail("d@vanep.com");
    user.setToken("tok-2");
    Driver driver = new Driver();
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    when(users.findByEmailAndDeletedAtIsNull("d@vanep.com")).thenReturn(Optional.of(user));
    when(drivers.findByUserId(2L)).thenReturn(Optional.of(driver));

    JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, "d@vanep.com");
    new JwtTokenCustomizer(users, drivers).customize(ctx);

    String status = ctx.getClaims().build().getClaim("driver_status");
    assertThat(status).isEqualTo("APPROVED");
  }

  @Test
  void skipsNonAccessTokens() {
    JwtEncodingContext ctx = context(OAuth2TokenType.REFRESH_TOKEN, "a@vanep.com");
    new JwtTokenCustomizer(users, drivers).customize(ctx);
    Object userType = ctx.getClaims().build().getClaim("user_type");
    assertThat(userType).isNull();
  }

  @Test
  void skipsUnknownUser() {
    when(users.findByEmailAndDeletedAtIsNull("x@vanep.com")).thenReturn(Optional.empty());
    JwtEncodingContext ctx = context(OAuth2TokenType.ACCESS_TOKEN, "x@vanep.com");
    new JwtTokenCustomizer(users, drivers).customize(ctx);
    Object uid = ctx.getClaims().build().getClaim("uid");
    assertThat(uid).isNull();
  }
}
