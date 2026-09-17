package br.com.vanep.clientrating.mapper;

import br.com.vanep.clientrating.dto.ClientRatingResponseDTO;
import br.com.vanep.clientrating.model.ClientRatingModel;
import org.springframework.stereotype.Component;

@Component
public class ClientRatingMapper {

  public ClientRatingResponseDTO toResponse(ClientRatingModel model) {
    return new ClientRatingResponseDTO(
        model.getToken(),
        model.getDriver().getToken(),
        model.getDriver().getUser().getName(),
        model.getClient().getToken(),
        model.getClient().getUser().getName(),
        model.getRating(),
        model.getComment(),
        model.getCreatedAt(),
        model.getUpdatedAt());
  }
}
