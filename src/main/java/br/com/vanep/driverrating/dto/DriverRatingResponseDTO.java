package br.com.vanep.driverrating.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DriverRatingResponseDTO(
    String token,
    String driverToken,
    String driverName,
    String clientToken,
    String clientName,
    BigDecimal rating,
    String comment,
    Instant createdAt,
    Instant updatedAt) {}
