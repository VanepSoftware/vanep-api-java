package br.com.vanep.auth.api;

import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProfileController {

  private final UserRepository users;

  public ProfileController(UserRepository users) {
    this.users = users;
  }

  @GetMapping("/api/user/profile")
  public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
    String email = jwt.getSubject();
    User user =
        users
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));

    return new ProfileResponse(
        user.getToken(), user.getName(), user.getEmail(), user.getType().name());
  }

  public record ProfileResponse(String token, String name, String email, String type) {}
}
