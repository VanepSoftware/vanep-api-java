package br.com.vanep.stopchange.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.stopchange.dto.StopChangeCreateRequestDTO;
import br.com.vanep.stopchange.dto.StopChangeResponseDTO;
import br.com.vanep.stopchange.mapper.StopChangeRequestMapper;
import br.com.vanep.stopchange.service.StopChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/clients/me/stop-change-requests")
public class ClientStopChangeRequestController {

  private final StopChangeRequestService service;
  private final StopChangeRequestMapper mapper;

  public ClientStopChangeRequestController(
      StopChangeRequestService service, StopChangeRequestMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('request_stop_change')")
  public StopChangeResponseDTO request(
      Authentication authentication, @Valid @RequestBody StopChangeCreateRequestDTO request) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return mapper.toResponse(service.requestChange(callerUid, request));
  }

  @GetMapping("/today")
  @PreAuthorize("isAuthenticated()")
  public List<StopChangeResponseDTO> today(Authentication authentication) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return service.listTodayForClient(callerUid).stream().map(mapper::toResponse).toList();
  }

  @PostMapping("/{token}/cancel")
  @PreAuthorize("hasAuthority('cancel_stop_change')")
  public StopChangeResponseDTO cancel(Authentication authentication, @PathVariable String token) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return mapper.toResponse(service.cancelChange(callerUid, token));
  }
}
