package br.com.vanep.familyinvite.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.familyinvite.dto.FamilyInviteAcceptRequestDTO;
import br.com.vanep.familyinvite.dto.FamilyInviteAcceptResponseDTO;
import br.com.vanep.familyinvite.dto.FamilyInvitePreviewResponseDTO;
import br.com.vanep.familyinvite.dto.FamilyInviteResponseDTO;
import br.com.vanep.familyinvite.service.FamilyInviteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FamilyInviteController {

  private final FamilyInviteService service;

  public FamilyInviteController(FamilyInviteService service) {
    this.service = service;
  }

  @PostMapping("/drivers/me/unlinked-passengers/{token}/invite")
  @PreAuthorize("isAuthenticated()")
  public FamilyInviteResponseDTO inviteFamily(
      Authentication authentication, @PathVariable String token) {
    return service.inviteFamily(SecurityHelper.requireCallerUid(authentication), token);
  }

  @GetMapping("/drivers/me/invite-link")
  @PreAuthorize("isAuthenticated()")
  public FamilyInviteResponseDTO findGroupInvite(Authentication authentication) {
    return service.findGroupInvite(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping("/family-invites/{code}")
  public FamilyInvitePreviewResponseDTO preview(@PathVariable String code) {
    return service.preview(code);
  }

  @PostMapping("/family-invites/{code}/accept")
  @PreAuthorize("isAuthenticated()")
  public FamilyInviteAcceptResponseDTO accept(
      Authentication authentication,
      @PathVariable String code,
      @Valid @RequestBody FamilyInviteAcceptRequestDTO request) {
    return service.accept(SecurityHelper.requireCallerUid(authentication), code, request);
  }
}
