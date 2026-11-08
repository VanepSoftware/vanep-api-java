package br.com.vanep.contract.controller;

import br.com.vanep.contract.dto.ContractResponseDTO;
import br.com.vanep.contract.service.ContractService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client-drivers/{token}/contracts")
public class ClientDriverContractController {

  private final ContractService service;

  public ClientDriverContractController(ContractService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(
      "hasAuthority('list_contracts') or @sec.isClientDriverLinkParty(#token, authentication)")
  public List<ContractResponseDTO> list(@PathVariable String token) {
    return service.findByClientDriverToken(token);
  }
}
