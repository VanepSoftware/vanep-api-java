package br.com.vanep.auth.api;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.user.dto.UserEmailChangeRequestDTO;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.dto.UserProfileUpdateRequestDTO;
import br.com.vanep.user.service.UserProfileService;
import br.com.vanep.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

  private final UserService userService;
  private final UserProfileService userProfileService;

  public ProfileController(UserService userService, UserProfileService userProfileService) {
    this.userService = userService;
    this.userProfileService = userProfileService;
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public UserMeResponseDTO me(Authentication authentication) {
    return userService.getMe(SecurityHelper.requireCallerUid(authentication));
  }

  @PatchMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public UserMeResponseDTO patchMe(
      Authentication authentication, @Valid @RequestBody UserProfileUpdateRequestDTO request) {
    return userProfileService.patchMe(SecurityHelper.requireCallerUid(authentication), request);
  }

  @PostMapping("/me/email-change")
  @PreAuthorize("isAuthenticated()")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestEmailChange(
      Authentication authentication, @Valid @RequestBody UserEmailChangeRequestDTO request) {
    userProfileService.requestEmailChange(SecurityHelper.requireCallerUid(authentication), request);
  }
}
