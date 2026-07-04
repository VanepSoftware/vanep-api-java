package br.com.vanep.rolepermission.dto;

import br.com.vanep.auth.security.validation.PermissionsInRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePermissionUpdateRequestDTO(
    @NotBlank @Size(max = 64) String name,
    @NotEmpty @PermissionsInRegistry List<String> permissions) {}
