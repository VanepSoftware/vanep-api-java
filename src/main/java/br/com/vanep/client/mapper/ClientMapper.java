package br.com.vanep.client.mapper;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.client.dto.ClientMeSummaryResponseDTO;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.user.dto.UserMeResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

  public ClientResponseDTO toResponse(ClientModel client, AddressResponseDTO address) {
    return new ClientResponseDTO(
        client.getToken(),
        client.getUser().getName(),
        client.getUser().getEmail(),
        client.getPhoto(),
        client.getRating(),
        address,
        client.isActive(),
        client.getCreatedAt());
  }

  public ClientMeSummaryResponseDTO toMeSummary(
      ClientModel client, UserMeResponseDTO user, AddressResponseDTO address) {
    return new ClientMeSummaryResponseDTO(
        client.getToken(), client.getPhoto(), client.getRating(), client.isActive(), user, address);
  }
}
