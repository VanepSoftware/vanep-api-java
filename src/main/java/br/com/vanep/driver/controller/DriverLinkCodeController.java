package br.com.vanep.driver.controller;

import br.com.vanep.assistant.service.AssistantLinkService;
import br.com.vanep.driver.dto.DriverLinkCodeConsumeRequestDTO;
import br.com.vanep.driver.dto.DriverLinkCodeGenerateResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/driver-link-codes")
public class DriverLinkCodeController {

  private final AssistantLinkService linkService;

  public DriverLinkCodeController(AssistantLinkService linkService) {
    this.linkService = linkService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_driver_link_code')")
  public DriverLinkCodeGenerateResponseDTO generate(@AuthenticationPrincipal Jwt jwt) {
    return linkService.generateLinkCode(jwt.getSubject());
  }

  @DeleteMapping("/current")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('cancel_driver_link_code')")
  public void cancel(@AuthenticationPrincipal Jwt jwt) {
    linkService.cancelCurrentLinkCode(jwt.getSubject());
  }

  @PostMapping("/consume")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('consume_driver_link_code')")
  public void consume(
      @Valid @RequestBody DriverLinkCodeConsumeRequestDTO request,
      @AuthenticationPrincipal Jwt jwt) {
    linkService.consumeLinkCode(jwt.getSubject(), request.code());
  }
}
