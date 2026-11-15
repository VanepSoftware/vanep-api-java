package br.com.vanep.city.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CityResponseDTO(
    String token,
    String name,
    String stateToken,
    String stateUf,
    BigDecimal cityLatitude,
    BigDecimal cityLongitude,
    boolean active,
    Instant createdAt) {}
