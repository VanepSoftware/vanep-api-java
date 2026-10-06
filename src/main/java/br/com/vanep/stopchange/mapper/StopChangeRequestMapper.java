package br.com.vanep.stopchange.mapper;

import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.stopchange.dto.StopChangeResponseDTO;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
import org.springframework.stereotype.Component;

@Component
public class StopChangeRequestMapper {

  private final AddressMapper addressMapper;

  public StopChangeRequestMapper(AddressMapper addressMapper) {
    this.addressMapper = addressMapper;
  }

  public StopChangeResponseDTO toResponse(StopChangeRequestModel model) {
    return new StopChangeResponseDTO(
        model.getToken(),
        model.getDependent() != null ? model.getDependent().getToken() : null,
        model.getDependent() != null ? model.getDependent().getName() : null,
        model.getTrip() != null ? model.getTrip().getToken() : null,
        model.getServiceDate(),
        model.getTrip() != null ? model.getTrip().getShift() : null,
        model.getNewDropoffAddress() != null
            ? addressMapper.toResponse(model.getNewDropoffAddress())
            : null,
        model.getReason(),
        model.getStatus(),
        model.getRequestedByUser() != null ? model.getRequestedByUser().getToken() : null,
        model.getRespondedByUser() != null ? model.getRespondedByUser().getToken() : null,
        model.getRespondedAt(),
        model.getCreatedAt(),
        model.getUpdatedAt());
  }
}
