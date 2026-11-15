package br.com.vanep.proposal.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.proposal.dto.ProposalAdjustmentRequestDTO;
import br.com.vanep.proposal.dto.ProposalDeclineRequestDTO;
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
@RequestMapping("/api/clients/me")
public class ClientProposalController {

  private final ProposalService service;

  public ClientProposalController(ProposalService service) {
    this.service = service;
  }

  @GetMapping("/service-requests/{token}/proposals")
  @PreAuthorize("isAuthenticated()")
  public List<ProposalResponseDTO> findChain(
      Authentication authentication, @PathVariable String token) {
    return service.findChainForClient(SecurityHelper.requireCallerUid(authentication), token);
  }

  @GetMapping("/proposals/{token}")
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO findMineByToken(
      Authentication authentication, @PathVariable String token) {
    return service.findForClient(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/proposals/{token}/accept")
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO accept(Authentication authentication, @PathVariable String token) {
    return service.accept(SecurityHelper.requireCallerUid(authentication), token);
  }

  @PostMapping("/proposals/{token}/decline")
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO decline(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody(required = false) ProposalDeclineRequestDTO request) {
    return service.decline(SecurityHelper.requireCallerUid(authentication), token, request);
  }

  @PostMapping("/proposals/{token}/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  public ProposalResponseDTO requestAdjustment(
      Authentication authentication,
      @PathVariable String token,
      @Valid @RequestBody ProposalAdjustmentRequestDTO request) {
    return service.requestAdjustment(
        SecurityHelper.requireCallerUid(authentication), token, request);
  }
}
