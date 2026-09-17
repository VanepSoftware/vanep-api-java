package br.com.vanep.clientrating.mapper;

import br.com.vanep.clientrating.dto.ClientRatingResponseDTO;
import br.com.vanep.clientrating.model.ClientRatingModel;
import org.springframework.stereotype.Component;

@Component
public class ClientRatingMapper {

  public ClientRatingResponseDTO toResponse(ClientRatingModel model) {
    return new ClientRatingResponseDTO(
        model.getToken(),
        model.getLink().getDriver().getToken(),
        model.getLink().getDriver().getUser().getName(),
        model.getLink().getClient().getToken(),
        model.getLink().getClient().getUser().getName(),
        model.getRating(),
        model.getComment(),
        model.getCreatedAt(),
        model.getUpdatedAt());
  }
}
