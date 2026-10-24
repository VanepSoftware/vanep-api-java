package br.com.vanep.servicerequest.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.servicerequest.dto.ServiceRequestDeclineRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drivers/me/service-requests")
public class DriverServiceRequestController {

  private final ServiceRequestService service;

  public DriverServiceRequestController(ServiceRequestService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ServiceRequestDriverResponseDTO> findMine(
      Authentication authentication,
      @RequestParam(required = false) Set<ServiceRequestStatus> status,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.findPageForDriver(
        SecurityHelper.requireCallerUid(authentication), status, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("isAuthenticated()")
  public ServiceRequestDriverResponseDTO findMineByToken(
      Authentication authentication, @PathVariable String token) {
    return service.findForDriver(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/{token}/decline")
  @PreAuthorize("isAuthenticated()")
  public ServiceRequestDriverResponseDTO decline(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody(required = false) ServiceRequestDeclineRequestDTO request) {
    return service.decline(SecurityHelper.requireCallerUid(authentication), token, request);
  }
}
