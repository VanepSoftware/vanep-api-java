package br.com.vanep.dependent.mapper;

import br.com.vanep.dependent.dto.DependentAddressDTO;
import br.com.vanep.dependent.dto.DependentClientDTO;
import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentSchoolDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.dependent.model.DependentModel;
import org.springframework.stereotype.Component;

@Component
public class DependentMapper {

  public DependentModel toModel(
      DependentCreateDTO dto, Long clientId, Long schoolId, Long addressId) {
    DependentModel model = new DependentModel();
    model.setClientId(clientId);
    model.setSchoolId(schoolId);
    model.setAddressId(addressId);
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

  public void applyUpdate(DependentUpdateDTO dto, DependentModel model) {
    if (dto.getName() != null) {
      model.setName(dto.getName());
    }
    if (dto.getBirthDate() != null) {
      model.setBirthDate(dto.getBirthDate());
    }
    if (dto.getGender() != null) {
      model.setGender(dto.getGender());
    }
    if (dto.getDocument() != null) {
      model.setDocument(dto.getDocument());
    }
    if (dto.getPhone() != null) {
      model.setPhone(dto.getPhone());
    }
    if (dto.getEmail() != null) {
      model.setEmail(dto.getEmail());
    }
    if (dto.getIsSelf() != null) {
      model.setSelf(dto.getIsSelf());
    }
    if (dto.getShift() != null) {
      model.setShift(dto.getShift());
    }
  }

  public DependentResponseDTO toResponse(
      DependentModel model, String clientToken, String schoolToken, String addressToken) {
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
        addressToken != null ? new DependentAddressDTO(addressToken) : null,
        model.getCreatedAt(),
        model.getUpdatedAt());
  }
}
