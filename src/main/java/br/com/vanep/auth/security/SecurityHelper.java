package br.com.vanep.auth.security;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

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

  public static String requireCallerUid(Authentication authentication) {
    return getCallerUid(authentication)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
  }
}
