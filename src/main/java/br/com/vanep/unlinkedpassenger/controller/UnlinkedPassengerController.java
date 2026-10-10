package br.com.vanep.unlinkedpassenger.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerCreateRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerScheduleRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerUpdateRequestDTO;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/unlinked-passengers")
public class UnlinkedPassengerController {

  private final UnlinkedPassengerService service;

  public UnlinkedPassengerController(UnlinkedPassengerService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<UnlinkedPassengerResponseDTO> findMine(Authentication authentication) {
    return service.findMine(SecurityHelper.requireCallerUid(authentication));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO create(
      Authentication authentication,
      @Valid @RequestBody UnlinkedPassengerCreateRequestDTO request) {
    return service.create(SecurityHelper.requireCallerUid(authentication), request);
  }

  @GetMapping("/{token}")
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO findMineByToken(
      Authentication authentication, @PathVariable String token) {
    return service.findMineByToken(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PatchMapping("/{token}")
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO update(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody UnlinkedPassengerUpdateRequestDTO request) {
    return service.update(SecurityHelper.requireCallerUid(authentication), token, request);
  }

  @PutMapping("/{token}/schedule")
  @PreAuthorize("isAuthenticated()")
  public UnlinkedPassengerResponseDTO replaceSchedule(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody UnlinkedPassengerScheduleRequestDTO request) {
    return service.replaceSchedule(SecurityHelper.requireCallerUid(authentication), token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void delete(Authentication authentication, @PathVariable String token) {
    service.delete(SecurityHelper.requireCallerUid(authentication), token);
  }
}
