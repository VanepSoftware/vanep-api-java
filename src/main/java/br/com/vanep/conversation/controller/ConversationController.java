package br.com.vanep.conversation.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.conversation.dto.ConversationCreateRequestDTO;
import br.com.vanep.conversation.dto.ConversationMessageCreateRequestDTO;
import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.dto.ConversationResponseDTO;
import br.com.vanep.conversation.service.ConversationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

  private final ConversationService service;

  public ConversationController(ConversationService service) {
    this.service = service;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('create_conversation')")
  public ResponseEntity<ConversationResponseDTO> open(
      @Valid @RequestBody ConversationCreateRequestDTO request, Authentication authentication) {
    ConversationService.OpenedConversation opened =
        service.openConversation(SecurityHelper.requireCallerUid(authentication), request);
    return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(opened.conversation());
  }

  @GetMapping("/me")
  @PreAuthorize("hasAuthority('list_driver_conversations')")
  public List<ConversationResponseDTO> mine(Authentication authentication) {
    return service.listDriverConversations(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping("/{token}/messages")
  @PreAuthorize("@sec.isConversationParty(#token, authentication)")
  public Page<ConversationMessageResponseDTO> history(
      @PathVariable String token, @PageableDefault(size = 30) Pageable pageable) {
    return service.listMessages(token, pageable);
  }

  @PostMapping("/{token}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@sec.isConversationParty(#token, authentication)")
  public ConversationMessageResponseDTO send(
      @PathVariable String token,
      @Valid @RequestBody ConversationMessageCreateRequestDTO request,
      Authentication authentication) {
    return service.sendMessage(SecurityHelper.requireCallerUid(authentication), token, request);
  }
}
