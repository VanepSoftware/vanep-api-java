package br.com.vanep.client.service;

import br.com.vanep.client.Client;
import br.com.vanep.client.ClientRepository;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

  public Page<ClientResponseDTO> findAll(Pageable pageable) {
    return clients.findAll(pageable).map(mapper::toResponse);
  }

  public ClientResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public ClientResponseDTO update(String token, ClientUpdateRequestDTO request) {
    Client client = requireByToken(token);
    client.setPhoto(request.photo());
    client.setAddressId(request.addressId());
    return mapper.toResponse(clients.save(client));
  }

  @Transactional
  public void delete(String token) {
    clients.delete(requireByToken(token));
  }

  private Client requireByToken(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
  }
}
