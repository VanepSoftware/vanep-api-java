package br.com.vanep.client.mapper;

import br.com.vanep.client.dto.ClientMeSummaryResponseDTO;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.user.dto.UserMeResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

  public ClientResponseDTO toResponse(ClientModel client, String addressToken) {
    return new ClientResponseDTO(
        client.getToken(),
        client.getUser().getName(),
        client.getUser().getEmail(),
        client.getPhoto(),
        client.getRating(),
        addressToken,
        client.isActive(),
        client.getCreatedAt());
  }

  public ClientResponseDTO toResponse(ClientModel client) {
    return toResponse(client, null);
  }

  public ClientMeSummaryResponseDTO toMeSummary(ClientModel client, UserMeResponseDTO user) {
    return new ClientMeSummaryResponseDTO(
        client.getToken(), client.getPhoto(), client.getRating(), client.isActive(), user);
  }
}
