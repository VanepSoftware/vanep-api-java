package br.com.vanep.driverrating.mapper;

import br.com.vanep.driverrating.dto.DriverRatingResponseDTO;
import br.com.vanep.driverrating.model.DriverRatingModel;
import org.springframework.stereotype.Component;

@Component
public class DriverRatingMapper {

  public DriverRatingResponseDTO toResponse(DriverRatingModel model) {
    return new DriverRatingResponseDTO(
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
