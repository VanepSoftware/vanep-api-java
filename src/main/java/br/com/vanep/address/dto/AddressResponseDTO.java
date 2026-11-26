package br.com.vanep.address.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AddressResponseDTO(
    String token,
    String zipCode,
    String street,
    String number,
    String complement,
    String neighborhood,
    String cityToken,
    String cityName,
    String stateUf,
    BigDecimal cityLatitude,
    BigDecimal cityLongitude,
    boolean active,
    Instant createdAt) {}
