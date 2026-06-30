package br.com.vanep.auth.oauth;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

  private final UserRepository users;
  private final DriverRepository drivers;

  public JwtTokenCustomizer(UserRepository users, DriverRepository drivers) {
    this.users = users;
    this.drivers = drivers;
  }

  @Override
  public void customize(JwtEncodingContext context) {
    if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
      return;
    }
    String email = context.getPrincipal().getName();
    Optional<User> maybe = users.findByEmail(email);
    if (maybe.isEmpty()) {
      return;
    }
    User user = maybe.get();
    context.getClaims().claim("uid", user.getToken());
    context.getClaims().claim("user_type", user.getType().name());
    context.getClaims().claim("roles", List.of("ROLE_" + user.getType().name()));
    if (user.getType() == UserType.DRIVER) {
      drivers
          .findByUserId(user.getId())
          .ifPresent(
              driver ->
                  context.getClaims().claim("driver_status", driver.getApprovalStatus().name()));
    }
  }
}
