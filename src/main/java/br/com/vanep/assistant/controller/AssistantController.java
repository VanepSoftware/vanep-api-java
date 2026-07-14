package br.com.vanep.assistant.controller;

import br.com.vanep.assistant.dto.AssistantInviteCreateRequestDTO;
import br.com.vanep.assistant.dto.AssistantInviteResponseDTO;
import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.service.AssistantInviteService;
import br.com.vanep.assistant.service.AssistantLinkService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistants")
public class AssistantController {

  private final AssistantInviteService inviteService;
  private final AssistantLinkService linkService;

  public AssistantController(
      AssistantInviteService inviteService, AssistantLinkService linkService) {
    this.inviteService = inviteService;
    this.linkService = linkService;
  }

  @PostMapping("/invites")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_assistant_invite')")
  public AssistantInviteResponseDTO createInvite(
      @Valid @RequestBody AssistantInviteCreateRequestDTO request,
      @AuthenticationPrincipal Jwt jwt) {
    return inviteService.createInvite(jwt.getSubject(), request);
  }

  @DeleteMapping("/invites/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('cancel_assistant_invite') and @assistantSecurity.ownsInvite(#token, authentication)")
  public void cancelInvite(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    inviteService.cancelInvite(jwt.getSubject(), token);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_assistants')")
  public List<AssistantListItemResponseDTO> list(@AuthenticationPrincipal Jwt jwt) {
    return linkService.listForDriver(jwt.getSubject());
  }

  @PostMapping("/{token}/pause")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('pause_assistant') and @assistantSecurity.isDriverOfAssistant(#token, authentication)")
  public void pause(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    linkService.pause(jwt.getSubject(), token);
  }

  @PostMapping("/{token}/resume")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('resume_assistant') and @assistantSecurity.isDriverOfAssistant(#token, authentication)")
  public void resume(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    linkService.resume(jwt.getSubject(), token);
  }

  @PostMapping("/{token}/revoke")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(
      "hasAuthority('revoke_assistant') and @assistantSecurity.isDriverOfAssistant(#token, authentication)")
  public void revokeByDriver(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    linkService.revokeByDriver(jwt.getSubject(), token);
  }

  @PostMapping("/me/revoke")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('revoke_assistant')")
  public void revokeSelf(@AuthenticationPrincipal Jwt jwt) {
    linkService.revokeSelf(jwt.getSubject());
  }
}
