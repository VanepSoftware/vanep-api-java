package br.com.vanep.contractreadiness.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.contractreadiness.dto.ContractReadinessResponseDTO;
import br.com.vanep.contractreadiness.service.ContractReadinessService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/me/contract-readiness")
public class ContractReadinessController {

  private final ContractReadinessService service;

  public ContractReadinessController(ContractReadinessService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ContractReadinessResponseDTO findMine(
      Authentication authentication, @RequestParam(required = false) List<String> dependentTokens) {
    return service.findReadiness(SecurityHelper.requireCallerUid(authentication), dependentTokens);
  }
}
