package br.com.vanep.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateRequestDTO(
    @NotBlank(message = "{role.name.required}") @Size(max = 64, message = "{role.name.too_long}")
        String name,
    String description,
    String rolePermissionToken) {}
