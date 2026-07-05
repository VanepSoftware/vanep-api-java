package br.com.vanep.role.dto;

import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import java.time.Instant;

public record RoleResponseDTO(
    String token,
    String name,
    String description,
    RolePermissionResponseDTO rolePermission,
    Instant createdAt) {}
