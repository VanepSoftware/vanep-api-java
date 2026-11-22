package br.com.vanep.role.dto;

import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record RoleResponseDTO(
    String token,
    String name,
    String description,
    @Nullable RolePermissionResponseDTO rolePermission,
    Instant createdAt) {}
