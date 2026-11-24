package br.com.vanep.clientday.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.clientday.dto.ClientDayResponseDTO;
import br.com.vanep.clientday.service.ClientDayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/me/day")
@PreAuthorize("isAuthenticated()")
public class ClientDayController {

  private final ClientDayService service;

  public ClientDayController(ClientDayService service) {
    this.service = service;
  }

  @GetMapping
  public ClientDayResponseDTO myDay(Authentication authentication) {
    return service.findMyDay(SecurityHelper.requireCallerUid(authentication));
  }
}
