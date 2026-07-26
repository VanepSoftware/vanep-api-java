package br.com.vanep.auth.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityHelper {

  private SecurityHelper() {
    // Utility class
  }

  public static Optional<String> getCallerUid(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      return Optional.ofNullable(jwtAuth.getToken().getClaim("uid"));
    }
    return Optional.empty();
  }
}
