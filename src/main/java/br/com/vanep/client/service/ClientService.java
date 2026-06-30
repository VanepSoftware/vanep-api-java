package br.com.vanep.client.service;

import br.com.vanep.client.dto.ClientDTO;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientService {

  private final ClientRepository clientRepository;
  private final UserRepository userRepository;

  public ClientDTO.Response create(Long userId, ClientDTO.Request dto) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

    if (clientRepository.existsByUserId(userId)) {
      throw new IllegalArgumentException("Este usuário já possui um client cadastrado");
    }

    ClientModel client = new ClientModel();
    client.setUser(user);
    client.setPhoto(dto.photo());

    ClientModel saved = clientRepository.save(client);

    return new ClientDTO.Response(saved);
  }
}