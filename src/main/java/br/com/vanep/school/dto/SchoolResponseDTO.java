package br.com.vanep.school.dto;

import java.time.Instant;

public record SchoolResponseDTO(
    String token,
    String name,
    String cnpj,
    String phone,
    String email,
    Long addressId,
    boolean active,
    Instant createdAt) {}
