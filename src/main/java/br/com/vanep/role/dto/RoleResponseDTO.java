package br.com.vanep.role.dto;

import java.time.Instant;

public record RoleResponseDTO(String token, String name, String description, Instant createdAt) {}
