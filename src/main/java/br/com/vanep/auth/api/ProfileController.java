package br.com.vanep.auth.api;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

  private final UserService userService;

  public ProfileController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public UserMeResponseDTO me(Authentication authentication) {
    return userService.getMe(SecurityHelper.requireCallerUid(authentication));
  }
}
