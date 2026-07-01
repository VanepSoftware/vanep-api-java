package br.com.vanep.client.mapper;

import br.com.vanep.client.Client;
import br.com.vanep.client.dto.ClientResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

  public ClientResponseDTO toResponse(Client client) {
    return new ClientResponseDTO(
        client.getToken(),
        client.getUser().getName(),
        client.getUser().getEmail(),
        client.getPhoto(),
        client.getRating(),
        null, // addressToken: pending Address entity implementation
        client.isActive(),
        client.getCreatedAt());
  }
}
