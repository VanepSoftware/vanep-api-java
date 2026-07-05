package br.com.vanep.role.mapper;

import br.com.vanep.role.dto.RoleResponseDTO;
import br.com.vanep.role.model.RoleModel;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

  public RoleResponseDTO toResponse(RoleModel role) {
    return new RoleResponseDTO(
        role.getToken(), role.getName(), role.getDescription(), role.getCreatedAt());
  }
}
