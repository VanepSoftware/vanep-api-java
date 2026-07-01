package br.com.vanep.role.mapper;

import br.com.vanep.role.Role;
import br.com.vanep.role.dto.RoleResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

  public RoleResponseDTO toResponse(Role role) {
    return new RoleResponseDTO(
        role.getToken(), role.getName(), role.getDescription(), role.getCreatedAt());
  }
}
