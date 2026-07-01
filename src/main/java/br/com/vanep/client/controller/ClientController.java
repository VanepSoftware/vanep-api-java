package br.com.vanep.client.controller;

import br.com.vanep.client.dto.ClientResponse;
import br.com.vanep.client.dto.ClientUpdateRequest;
import br.com.vanep.client.service.ClientService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
  public Page<ClientResponse> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  public ClientResponse get(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
    return service.findByToken(token, jwt);
  }

  @PutMapping("/{token}")
  public ClientResponse update(
      @PathVariable String token,
      @RequestBody ClientUpdateRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return service.update(token, request, jwt);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
