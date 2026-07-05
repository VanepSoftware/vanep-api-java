package br.com.vanep.auth.oauth;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.role.repository.RoleRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
  private final RoleRepository roles;

  public JwtTokenCustomizer(UserRepository users, DriverRepository drivers, RoleRepository roles) {
    this.users = users;
    this.drivers = drivers;
    this.roles = roles;
  }

  @Override
  public void customize(JwtEncodingContext context) {
    if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
      return;
    }
    String email = context.getPrincipal().getName();
    Optional<UserModel> maybe = users.findByEmail(email);
    if (maybe.isEmpty()) {
      return;
    }
    UserModel user = maybe.get();
    context.getClaims().claim("uid", user.getToken());
    context.getClaims().claim("user_type", user.getType().name());
    context.getClaims().claim("roles", List.of("ROLE_" + user.getType().name()));
    context.getClaims().claim("permissions", resolvePermissions(user));
    if (user.getType() == UserType.DRIVER) {
      drivers
          .findByUserId(user.getId())
          .ifPresent(
              driver ->
                  context.getClaims().claim("driver_status", driver.getApprovalStatus().name()));
    }
  }

  private List<String> resolvePermissions(UserModel user) {
    if (user.getRoleId() == null) {
      return List.of();
    }
    return roles
        .findById(user.getRoleId())
        .map(role -> role.getRolePermission())
        .map(bundle -> List.copyOf(bundle.getPermissions()))
        .orElse(List.of());
  }
}
