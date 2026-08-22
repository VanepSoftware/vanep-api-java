package br.com.vanep.dependent.mapper;

import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.dependent.dto.DependentClientDTO;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.model.DependentModel;
import org.springframework.stereotype.Component;

@Component
public class DependentMapper {

  public DependentModel toModel(DependentCreateDTO dto, Long clientId) {
    DependentModel model = new DependentModel();
    model.setClientId(clientId);
    model.setName(dto.getName());
    model.setBirthDate(dto.getBirthDate());
    model.setGender(dto.getGender());
    model.setDocument(dto.getDocument());
    model.setPhone(dto.getPhone());
    model.setEmail(dto.getEmail());
    model.setSelf(Boolean.TRUE.equals(dto.getIsSelf()));
    model.setShift(dto.getShift() != null ? dto.getShift() : Shift.MORNING);
    return model;
  }

  public DependentResponseDTO toResponse(
      DependentModel model, String clientToken, String schoolToken, AddressResponseDTO address) {
    return new DependentResponseDTO(
        model.getToken(),
        new DependentClientDTO(clientToken),
        model.getName(),
        model.getBirthDate(),
        model.getGender(),
        model.getDocument(),
        model.getPhone(),
        model.getEmail(),
        model.isSelf(),
        model.isDefaultDependent(),
        model.getShift(),
        schoolToken != null ? new DependentSchoolDTO(schoolToken) : null,
        address,
        model.getCreatedAt(),
        model.getUpdatedAt());
  }
}
