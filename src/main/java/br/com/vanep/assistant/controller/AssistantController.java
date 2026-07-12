package br.com.vanep.assistant.controller;

import br.com.vanep.assistant.dto.AssistantListItemResponseDTO;
import br.com.vanep.assistant.service.AssistantLinkService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistants")
public class AssistantController {

  private final AssistantLinkService service;

  public AssistantController(AssistantLinkService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_assistants')")
  public List<AssistantListItemResponseDTO> list(@AuthenticationPrincipal Jwt jwt) {
    return service.listForDriver(jwt.getSubject());
  }

  @PostMapping("/{token}/pause")
  @PreAuthorize("hasAuthority('pause_assistant')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void pause(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    service.pause(token, jwt.getSubject());
  }

  @PostMapping("/{token}/resume")
  @PreAuthorize("hasAuthority('resume_assistant')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resume(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    service.resume(token, jwt.getSubject());
  }

  @PostMapping("/{token}/revoke")
  @PreAuthorize("hasAuthority('revoke_assistant')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    service.revoke(token, jwt.getSubject());
  }

  @PostMapping("/me/revoke")
  @PreAuthorize("@assistantSecurity.isSelfAssistant(authentication)")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeSelf(@AuthenticationPrincipal Jwt jwt) {
    service.revokeByAssistant(jwt.getSubject());
  }
}
