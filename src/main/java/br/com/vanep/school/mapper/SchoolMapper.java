package br.com.vanep.school.mapper;

import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.model.SchoolModel;
import org.springframework.stereotype.Component;

@Component
public class SchoolMapper {

  public SchoolResponseDTO toResponse(SchoolModel school) {
    return new SchoolResponseDTO(
        school.getToken(),
        school.getName(),
        school.getCnpj(),
        school.getPhone(),
        school.getEmail(),
        school.getAddressId(),
        school.isActive(),
        school.getCreatedAt());
  }
}
