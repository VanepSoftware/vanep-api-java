package br.com.vanep.clientdriver.controller;

import br.com.vanep.auth.security.SecurityHelper;
import br.com.vanep.clientdriver.dto.ClientDriverCreateRequestDTO;
import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.dto.ClientDriverUpdateRequestDTO;
import br.com.vanep.clientdriver.service.ClientDriverService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/client-drivers")
public class ClientDriverController {

  private final ClientDriverService service;

  public ClientDriverController(ClientDriverService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('create_client_driver')")
  public ClientDriverResponseDTO create(@Valid @RequestBody ClientDriverCreateRequestDTO request) {
    return service.create(request);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('list_client_drivers')")
  public Page<ClientDriverResponseDTO> list(@PageableDefault Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public List<ClientDriverResponseDTO> mine(Authentication authentication) {
    return service.findMine(SecurityHelper.requireCallerUid(authentication));
  }

  @GetMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('show_client_driver') or @sec.isClientDriverLinkParty(#token, authentication)")
  public ClientDriverResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PatchMapping("/{token}")
  @PreAuthorize(
      "hasAuthority('update_client_driver') or @sec.isClientDriverLinkParty(#token, authentication)")
  public ClientDriverResponseDTO update(
      @PathVariable String token, @Valid @RequestBody ClientDriverUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('delete_client_driver')")
  public void delete(@PathVariable String token) {
    service.delete(token);
  }

  @PostMapping("/{token}/restore")
  @PreAuthorize("hasAuthority('restore_client_driver')")
  public ClientDriverResponseDTO restore(@PathVariable String token) {
    return service.restore(token);
  }
}
