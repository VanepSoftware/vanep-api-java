package br.com.vanep.notification.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.notification.dto.DeviceTokenRegisterRequestDTO;
import br.com.vanep.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@PreAuthorize("isAuthenticated()")
public class DeviceTokenController {

  private final DeviceTokenService service;

  public DeviceTokenController(DeviceTokenService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void register(
      @Valid @RequestBody DeviceTokenRegisterRequestDTO request, Authentication authentication) {
    service.register(SecurityHelper.requireCallerUid(authentication), request);
  }

  @DeleteMapping("/{fcmToken}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(@PathVariable String fcmToken, Authentication authentication) {
    service.unregister(SecurityHelper.requireCallerUid(authentication), fcmToken);
  }
}
