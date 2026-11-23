package br.com.vanep.notification.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.notification.dto.NotificationResponseDTO;
import br.com.vanep.notification.dto.NotificationUnreadCountResponseDTO;
import br.com.vanep.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public Page<NotificationResponseDTO> mine(
      @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
    return service.listMine(SecurityHelper.requireCallerUid(authentication), pageable);
  }

  @GetMapping("/unread-count")
  public NotificationUnreadCountResponseDTO unreadCount(Authentication authentication) {
    return new NotificationUnreadCountResponseDTO(
        service.countMyUnread(SecurityHelper.requireCallerUid(authentication)));
  }

  @PostMapping("/{token}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@sec.isNotificationOwner(#token, authentication)")
  public void markRead(@PathVariable String token) {
    service.markRead(token);
  }

  @PostMapping("/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllRead(Authentication authentication) {
    service.markAllMineRead(SecurityHelper.requireCallerUid(authentication));
  }
}
