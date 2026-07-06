package br.com.vanep.client.mapper;

import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.model.ClientModel;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

  public ClientResponseDTO toResponse(ClientModel client) {
    return new ClientResponseDTO(
        client.getToken(),
        client.getUser().getName(),
        client.getUser().getEmail(),
        client.getPhoto(),
        client.getRating(),
        null,
        client.isActive(),
        client.getCreatedAt());
  }
}
