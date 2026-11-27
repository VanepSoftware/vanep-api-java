package br.com.vanep.rolepermission.dto;

import br.com.vanep.auth.security.validation.PermissionsInRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePermissionUpdateRequestDTO(
    @NotBlank(message = "{role_permission.name.required}")
        @Size(max = 64, message = "{role_permission.name.too_long}")
        String name,
    @NotEmpty(message = "{role_permission.permissions.required}") @PermissionsInRegistry
        List<String> permissions) {}
