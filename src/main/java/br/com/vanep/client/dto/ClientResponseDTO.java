package br.com.vanep.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClientResponseDTO(
    String token,
    String name,
    String email,
    String photo,
    BigDecimal rating,
    String addressToken,
    boolean active,
    Instant createdAt) {}
