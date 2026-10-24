package br.com.vanep.servicerequest.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestCreateRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/me/service-requests")
public class ClientServiceRequestController {

  private final ServiceRequestService service;

  public ClientServiceRequestController(ServiceRequestService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public ServiceRequestClientResponseDTO send(
      Authentication authentication, @Valid @RequestBody ServiceRequestCreateRequestDTO request) {
    return service.send(SecurityHelper.requireCallerUid(authentication), request);
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public Page<ServiceRequestClientResponseDTO> findMine(
      Authentication authentication,
      @RequestParam(required = false) Set<ServiceRequestStatus> status,
      @PageableDefault(size = 20) Pageable pageable) {
    return service.findPageForClient(
        SecurityHelper.requireCallerUid(authentication), status, pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("isAuthenticated()")
  public ServiceRequestClientResponseDTO findMineByToken(
      Authentication authentication, @PathVariable String token) {
    return service.findForClient(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/{token}/withdraw")
  @PreAuthorize("isAuthenticated()")
  public ServiceRequestClientResponseDTO withdraw(
      Authentication authentication, @PathVariable String token) {
    return service.withdraw(SecurityHelper.requireCallerUid(authentication), token);
  }
}
