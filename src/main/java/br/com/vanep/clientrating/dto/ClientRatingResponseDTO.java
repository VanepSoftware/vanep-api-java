package br.com.vanep.clientrating.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClientRatingResponseDTO(
    String token,
    String driverToken,
    String driverName,
    String clientToken,
    String clientName,
    BigDecimal rating,
    String comment,
    Instant createdAt,
    Instant updatedAt) {}
