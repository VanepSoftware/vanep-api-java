package br.com.vanep.client.mapper;

import br.com.vanep.client.Client;
import br.com.vanep.client.dto.ClientResponse;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

  public ClientResponse toResponse(Client client) {
    return new ClientResponse(
        client.getToken(),
        client.getUser().getName(),
        client.getUser().getEmail(),
        client.getPhoto(),
        client.getRating(),
        client.getAddressId(),
        client.isActive(),
        client.getCreatedAt());
  }
}
