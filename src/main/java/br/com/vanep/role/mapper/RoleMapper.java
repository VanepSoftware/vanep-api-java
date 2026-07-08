package br.com.vanep.role.mapper;

import br.com.vanep.role.dto.RoleResponseDTO;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.rolepermission.mapper.RolePermissionMapper;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

  private final RolePermissionMapper rolePermissionMapper;

  public RoleMapper(RolePermissionMapper rolePermissionMapper) {
    this.rolePermissionMapper = rolePermissionMapper;
  }

  public RoleResponseDTO toResponse(RoleModel role) {
    return new RoleResponseDTO(
        role.getToken(),
        role.getName(),
        role.getDescription(),
        role.getRolePermission() == null
            ? null
            : rolePermissionMapper.toResponse(role.getRolePermission()),
        role.getCreatedAt());
  }
}
