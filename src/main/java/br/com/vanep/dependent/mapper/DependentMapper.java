package br.com.vanep.dependent.mapper;

import br.com.vanep.dependent.dto.DependentCreateDTO;
import br.com.vanep.dependent.dto.DependentResponseDTO;
import br.com.vanep.dependent.dto.DependentUpdateDTO;
import br.com.vanep.dependent.entity.DependentEntity;
import br.com.vanep.dependent.enums.Shift;
import org.springframework.stereotype.Component;

@Component
public class DependentMapper {

  public DependentEntity toEntity(DependentCreateDTO dto, Long clientId) {
    DependentEntity entity = new DependentEntity();
    entity.setClientId(clientId);
    entity.setName(dto.getName());
    entity.setBirthDate(dto.getBirthDate());
    entity.setGender(dto.getGender());
    entity.setDocument(dto.getDocument());
    entity.setPhone(dto.getPhone());
    entity.setEmail(dto.getEmail());
    entity.setSelf(Boolean.TRUE.equals(dto.getIsSelf()));
    entity.setShift(dto.getShift() != null ? dto.getShift() : Shift.MORNING);
    entity.setSchoolId(dto.getSchoolId());
    entity.setAddressId(dto.getAddressId());
    return entity;
  }

  public void applyUpdate(DependentUpdateDTO dto, DependentEntity entity) {
    if (dto.getName() != null) {
      entity.setName(dto.getName());
    }
    if (dto.getBirthDate() != null) {
      entity.setBirthDate(dto.getBirthDate());
    }
    if (dto.getGender() != null) {
      entity.setGender(dto.getGender());
    }
    if (dto.getDocument() != null) {
      entity.setDocument(dto.getDocument());
    }
    if (dto.getPhone() != null) {
      entity.setPhone(dto.getPhone());
    }
    if (dto.getEmail() != null) {
      entity.setEmail(dto.getEmail());
    }
    if (dto.getIsSelf() != null) {
      entity.setSelf(dto.getIsSelf());
    }
    if (dto.getShift() != null) {
      entity.setShift(dto.getShift());
    }
    if (dto.getSchoolId() != null) {
      entity.setSchoolId(dto.getSchoolId());
    }
    if (dto.getAddressId() != null) {
      entity.setAddressId(dto.getAddressId());
    }
  }

  public DependentResponseDTO toResponse(DependentEntity entity) {
    return new DependentResponseDTO(
        entity.getToken(),
        entity.getClientId(),
        entity.getName(),
        entity.getBirthDate(),
        entity.getGender(),
        entity.getDocument(),
        entity.getPhone(),
        entity.getEmail(),
        entity.isSelf(),
        entity.isDefaultDependent(),
        entity.getShift(),
        entity.getSchoolId(),
        entity.getAddressId(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
