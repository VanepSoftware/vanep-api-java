package br.com.vanep.stopchange.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.stopchange.dto.StopChangeResponseDTO;
import br.com.vanep.stopchange.mapper.StopChangeRequestMapper;
import br.com.vanep.stopchange.service.StopChangeRequestService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/stop-change-requests")
public class DriverStopChangeRequestController {

  private final StopChangeRequestService service;
  private final StopChangeRequestMapper mapper;

  public DriverStopChangeRequestController(
      StopChangeRequestService service, StopChangeRequestMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping("/today")
  @PreAuthorize("isAuthenticated()")
  public List<StopChangeResponseDTO> today(Authentication authentication) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return service.listTodayForDriver(callerUid).stream().map(mapper::toResponse).toList();
  }

  @PostMapping("/{token}/approve")
  @PreAuthorize("hasAuthority('approve_stop_change')")
  public StopChangeResponseDTO approve(Authentication authentication, @PathVariable String token) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return mapper.toResponse(service.approveChange(callerUid, token));
  }

  @PostMapping("/{token}/reject")
  @PreAuthorize("hasAuthority('reject_stop_change')")
  public StopChangeResponseDTO reject(Authentication authentication, @PathVariable String token) {
    String callerUid = SecurityHelper.requireCallerUid(authentication);
    return mapper.toResponse(service.rejectChange(callerUid, token));
  }
}
