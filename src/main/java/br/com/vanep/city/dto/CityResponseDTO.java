package br.com.vanep.city.dto;

import java.time.Instant;

public record CityResponseDTO(
    String token,
    String name,
    String stateToken,
    String stateUf,
    boolean active,
    Instant createdAt) {}
