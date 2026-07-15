package br.com.vanep.client.controller;

import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.service.ClientService;
import jakarta.validation.Valid;
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
  @PreAuthorize("hasAuthority('list_clients')")
  public Page<ClientResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
    return service.findAll(pageable);
  }

  @GetMapping("/{token}")
  @PreAuthorize("hasAuthority('show_client') or @clientSecurity.isOwner(#token, authentication)")
  public ClientResponseDTO get(@PathVariable String token) {
    return service.findByToken(token);
  }

  // ALTERADO (painel admin #11):
  // - autorização: antes era só o dono (@clientSecurity.isOwner). Adicionado
  //   "update_client OR isOwner" para o admin poder editar clientes pelo painel,
  //   sem tirar a edição do próprio perfil pelo dono.
  // - @Valid: passa a validar o corpo (e-mail, tamanho do nome, faixa da nota).
  @PutMapping("/{token}")
  @PreAuthorize("hasAuthority('update_client') or @clientSecurity.isOwner(#token, authentication)")
  public ClientResponseDTO update(
      @PathVariable String token, @RequestBody @Valid ClientUpdateRequestDTO request) {
    return service.update(token, request);
  }

  @DeleteMapping("/{token}")
  @PreAuthorize("hasAuthority('delete_client')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    service.delete(token);
  }
}
