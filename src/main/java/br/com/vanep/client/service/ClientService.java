package br.com.vanep.client.service;

import br.com.vanep.client.Client;
import br.com.vanep.client.ClientRepository;
import br.com.vanep.client.dto.ClientResponse;
import br.com.vanep.client.dto.ClientUpdateRequest;
import br.com.vanep.client.mapper.ClientMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {

  private final ClientRepository clients;
  private final ClientMapper mapper;

  public ClientService(ClientRepository clients, ClientMapper mapper) {
    this.clients = clients;
    this.mapper = mapper;
  }

  public Page<ClientResponse> findAll(Pageable pageable) {
    return clients.findAll(pageable).map(mapper::toResponse);
  }

  public ClientResponse findByToken(String token, Jwt jwt) {
    Client client = requireByToken(token);
    requireAdminOrOwner(client, jwt);
    return mapper.toResponse(client);
  }

  @Transactional
  public ClientResponse update(String token, ClientUpdateRequest request, Jwt jwt) {
    Client client = requireByToken(token);
    requireOwner(client, jwt);
    client.setPhoto(request.photo());
    client.setAddressId(request.addressId());
    return mapper.toResponse(clients.save(client));
  }

  @Transactional
  public void delete(String token) {
    Client client = requireByToken(token);
    clients.delete(client);
  }

  private Client requireByToken(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
  }

  // caller uid from JWT must match the client's user token
  private void requireAdminOrOwner(Client client, Jwt jwt) {
    if (isAdmin(jwt)) return;
    requireOwner(client, jwt);
  }

  private void requireOwner(Client client, Jwt jwt) {
    String callerUid = jwt.getClaim("uid");
    String ownerToken = client.getUser().getToken();
    if (!ownerToken.equals(callerUid)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
    }
  }

  private boolean isAdmin(Jwt jwt) {
    Object roles = jwt.getClaim("roles");
    if (roles instanceof Iterable<?> list) {
      for (Object r : list) {
        if ("ROLE_ADMIN".equals(r.toString())) return true;
      }
    }
    return false;
  }
}
