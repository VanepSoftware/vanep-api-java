package br.com.vanep.contract.controller;

import br.com.vanep.contract.dto.ContractCreateRequestDTO;
import br.com.vanep.contract.dto.ContractResponseDTO;
import br.com.vanep.contract.dto.ContractUpdateRequestDTO;
import br.com.vanep.contract.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

  private final ContractService service;

  public ContractController(ContractService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_contract')")
  public ContractResponseDTO create(@Valid @RequestBody ContractCreateRequestDTO request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_contracts')")
  public Page<ContractResponseDTO> list(@PageableDefault Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_contract') or @sec.isContractParty(#token, authentication)")
  public ContractResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PatchMapping("/{token}")
  @PreAuthorize("hasAuthority('update_contract')")
  public ContractResponseDTO update(
      @PathVariable String token, @Valid @RequestBody ContractUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_contract')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_contract')")
  public ContractResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
