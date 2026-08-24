package br.com.vanep.school.mapper;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.model.SchoolModel;
import org.springframework.stereotype.Component;

@Component
public class SchoolMapper {

  public SchoolResponseDTO toResponse(SchoolModel school) {
    return toResponse(school, null);
  }

  public SchoolResponseDTO toResponse(SchoolModel school, AddressResponseDTO address) {
    return new SchoolResponseDTO(
        school.getToken(),
        school.getName(),
        school.getGooglePlaceId(),
        school.getCity() == null ? null : school.getCity().getName(),
        school.getCity() == null ? null : school.getCity().getToken(),
        school.getDistrict() == null ? null : school.getDistrict().getName(),
        school.getDistrict() == null ? null : school.getDistrict().getToken(),
        address,
        school.isActive(),
        school.getCreatedAt());
  }
}
