package br.com.vanep.clientdriver.mapper;

import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.enums.LinkParty;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import org.springframework.stereotype.Component;

@Component
public class ClientDriverMapper {

  public ClientDriverResponseDTO toResponse(ClientDriverModel link) {
    return new ClientDriverResponseDTO(
        link.getToken(),
        link.getClient().getToken(),
        link.getClient().getUser().getName(),
        link.getDriver().getToken(),
        link.getDriver().getUser().getName(),
        link.getStatus(),
        link.getOrigin(),
        blockerOf(link),
        link.getCreatedAt(),
        link.getUpdatedAt());
  }

  LinkParty blockerOf(ClientDriverModel link) {
    if (link.getBlockedBy() == null) {
      return null;
    }
    return link.getBlockedBy().getId().equals(link.getClient().getUser().getId())
        ? LinkParty.CLIENT
        : LinkParty.DRIVER;
  }
}
