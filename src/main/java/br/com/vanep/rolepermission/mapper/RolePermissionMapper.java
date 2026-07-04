package br.com.vanep.rolepermission.mapper;

import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionMapper {

  public RolePermissionResponseDTO toResponse(RolePermissionModel bundle) {
    return new RolePermissionResponseDTO(
        bundle.getToken(), bundle.getName(), bundle.getPermissions(), bundle.getCreatedAt());
  }
}
