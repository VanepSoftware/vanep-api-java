package br.com.vanep.assistant.controller;

import br.com.vanep.assistant.dto.AssistantInvitePageDTO;
import br.com.vanep.assistant.service.AssistantInviteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AssistantInviteWebController {

  private final AssistantInviteService inviteService;

  public AssistantInviteWebController(AssistantInviteService inviteService) {
    this.inviteService = inviteService;
  }

  @GetMapping("/assistant-invite/{token}")
  public String showInvite(@PathVariable String token, Authentication authentication, Model model) {
    AssistantInvitePageDTO page =
        inviteService.resolveInvitePage(token, callerEmail(authentication));
    populateModel(model, token, page);
    return "assistant-invite";
  }

  @PostMapping("/assistant-invite/{token}/accept")
  public String accept(@PathVariable String token, Authentication authentication) {
    inviteService.acceptInvite(token, requireCallerEmail(authentication));
    return "redirect:/login?invite-accepted";
  }

  @PostMapping("/assistant-invite/{token}/reject")
  public String reject(@PathVariable String token, Authentication authentication) {
    inviteService.rejectInvite(token, requireCallerEmail(authentication));
    return "redirect:/login?invite-rejected";
  }

  private void populateModel(Model model, String token, AssistantInvitePageDTO page) {
    model.addAttribute("token", token);
    model.addAttribute("state", page.state());
    model.addAttribute("driverName", page.driverName());
    model.addAttribute("driverPhoto", page.driverPhoto());
    model.addAttribute("driverCity", page.driverCity());
    model.addAttribute("driverRating", page.driverRating());
    model.addAttribute("expiresAt", page.expiresAt());
  }

  private String callerEmail(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    return authentication.getName();
  }

  private String requireCallerEmail(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new IllegalStateException("Authentication required");
    }
    return authentication.getName();
  }
}
