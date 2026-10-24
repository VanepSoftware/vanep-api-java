package br.com.vanep.proposal.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.proposal.dto.ProposalCreateRequestDTO;
import br.com.vanep.proposal.dto.ProposalResponseDTO;
import br.com.vanep.proposal.service.ProposalService;
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
@RequestMapping("/api/drivers/me")
public class DriverProposalController {

  private final ProposalService service;

  public DriverProposalController(ProposalService service) {
    this.service = service;
  }

  @PostMapping("/service-requests/{token}/proposals")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO send(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody ProposalCreateRequestDTO request) {
    return service.send(SecurityHelper.requireCallerUid(authentication), token, request);
  }

  @GetMapping("/service-requests/{token}/proposals")
  @PreAuthorize("isAuthenticated()")
  public List<ProposalResponseDTO> findChain(
      Authentication authentication, @PathVariable String token) {
    return service.findChainForDriver(SecurityHelper.requireCallerUid(authentication), token);
  }

  @GetMapping("/proposals/{token}")
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO findMineByToken(
      Authentication authentication, @PathVariable String token) {
    return service.findForDriver(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/proposals/{token}/withdraw")
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO withdraw(Authentication authentication, @PathVariable String token) {
    return service.withdraw(SecurityHelper.requireCallerUid(authentication), token);
  }
}
