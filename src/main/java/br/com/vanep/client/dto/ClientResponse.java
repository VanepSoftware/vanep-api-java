package br.com.vanep.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ClientResponse(
    String token,
    String name,
    String email,
    String photo,
    BigDecimal rating,
    Long addressId,
    boolean active,
    Instant createdAt) {}
