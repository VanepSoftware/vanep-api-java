package br.com.vanep.rolepermission.dto;

import java.time.Instant;
import java.util.List;

public record RolePermissionResponseDTO(
    String token, String name, List<String> permissions, Instant createdAt) {}
