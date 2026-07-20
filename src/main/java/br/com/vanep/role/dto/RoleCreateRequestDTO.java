package br.com.vanep.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleCreateRequestDTO(
    @NotBlank @Size(max = 64) String name, String description, String rolePermissionToken) {}
