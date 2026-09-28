package br.com.vanep.cep.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.cep.dto.CepLookupResponseDTO;
import br.com.vanep.cep.service.CepLookupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cep")
public class CepLookupController {
  private final CepLookupService service;

  public CepLookupController(CepLookupService service) {
    this.service = service;
  }

  @GetMapping("/{cep}")
  @PreAuthorize("isAuthenticated()")
  public CepLookupResponseDTO lookup(Authentication authentication, @PathVariable String cep) {
    return service.lookup(SecurityHelper.requireCallerUid(authentication), cep);
  }
}
