package br.com.vanep.client.controller;

import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.service.ClientService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

  private final ClientService service;

  public ClientController(ClientService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Page<ClientResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasRole('ADMIN') or @clientSecurity.isOwner(#token, authentication)")
  public ClientResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  @PutMapping("/{token}")
  @PreAuthorize("@clientSecurity.isOwner(#token, authentication)")
  public ClientResponseDTO update(
      @PathVariable String token, @RequestBody ClientUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
