package br.com.vanep.client.service;

import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.model.UserModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {

  private final ClientRepository clients;
  // ALTERADO (painel admin #11): UserRepository injetado para checar duplicidade
  // de e-mail (o e-mail é único e mora em User, não em Client).
  private final UserRepository users;
  private final ClientMapper mapper;

  public ClientService(ClientRepository clients, UserRepository users, ClientMapper mapper) {
    this.clients = clients;
    this.users = users;
    this.mapper = mapper;
  }

  public Page<ClientResponseDTO> findAll(Pageable pageable) {
    return clients.findAll(pageable).map(mapper::toResponse);
  }

  public ClientResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  // ALTERADO (painel admin #11): antes o update só fazia client.setPhoto(...).
  // Agora aplica name/email (que ficam em User), rating e active. Cada campo só é
  // sobrescrito quando vem preenchido, para o PUT parcial não zerar o que não foi
  // enviado.
  @Transactional
  public ClientResponseDTO update(String token, ClientUpdateRequestDTO request) {
    ClientModel client = requireByToken(token);
    UserModel user = client.getUser();

    if (request.name() != null && !request.name().isBlank()) {
      user.setName(request.name());
    }
    if (request.email() != null && !request.email().isBlank()) {
      applyEmail(user, request.email());
    }
    if (request.rating() != null) {
      client.setRating(request.rating());
    }
    if (request.active() != null) {
      client.setActive(request.active());
    }
    client.setPhoto(request.photo());
    return mapper.toResponse(clients.save(client));
  }

  // ALTERADO (painel admin #11): valida a troca de e-mail. Como o e-mail é único
  // no sistema, tentar usar um já cadastrado por outro usuário retorna 409 em vez
  // de estourar erro de constraint do banco.
  private void applyEmail(UserModel user, String email) {
    if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmail(email)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "E-mail já cadastrado para outro usuário.");
    }
    user.setEmail(email);
  }

  @Transactional
  public void delete(String token) {
    clients.delete(requireByToken(token));
  }

  private ClientModel requireByToken(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
  }
}
