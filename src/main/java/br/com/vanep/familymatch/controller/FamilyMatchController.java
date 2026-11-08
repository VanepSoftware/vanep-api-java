package br.com.vanep.familymatch.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.familymatch.service.FamilyMatchService;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/unlinked-passengers/{token}")
public class FamilyMatchController {

  private final FamilyMatchService service;

  public FamilyMatchController(FamilyMatchService service) {
    this.service = service;
  }

  @PostMapping("/merge")
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO merge(
      Authentication authentication, @PathVariable String token) {
    return service.merge(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/family-match/dismiss")
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO dismiss(
      Authentication authentication, @PathVariable String token) {
    return service.dismiss(SecurityHelper.requireCallerUid(authentication), token);
  }
}
