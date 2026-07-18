package br.com.vanep.address.mapper;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.model.AddressModel;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

  public AddressResponseDTO toResponse(AddressModel address) {
    return new AddressResponseDTO(
        address.getToken(),
        address.getZipCode(),
        address.getStreet(),
        address.getNumber(),
        address.getComplement(),
        address.getDistrict(),
        address.getCity().getToken(),
        address.getCity().getName(),
        address.getCity().getState().getUf(),
        address.isActive(),
        address.getCreatedAt());
  }
}
