package br.com.vanep.country.dto;

import java.time.Instant;

public record CountryResponseDTO(
    String token,
    String name,
    String isoCode,
    String phoneCode,
    String currency,
    String locale,
    boolean active,
    Instant createdAt) {}
