package br.com.vanep.trip.mapper;

import br.com.vanep.trip.dto.TripResponseDTO;
import br.com.vanep.trip.model.TripModel;
import org.springframework.stereotype.Component;

@Component
public class TripMapper {

  public TripResponseDTO toResponse(TripModel trip, boolean outsideWorkWindow) {
    return new TripResponseDTO(
        trip.getToken(),
        trip.getDriver() != null ? trip.getDriver().getToken() : null,
        trip.getServiceDate(),
        trip.getShift(),
        trip.getStatus(),
        trip.getStartedAt(),
        trip.getFinishedAt(),
        outsideWorkWindow,
        trip.getCreatedAt(),
        trip.getUpdatedAt());
  }
}
