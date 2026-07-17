package br.com.vanep.address.dto;

import java.time.Instant;

public record AddressResponseDTO(
    String token,
    String zipCode,
    String street,
    String number,
    String complement,
    String district,
    String cityToken,
    String cityName,
    String stateUf,
    boolean active,
    Instant createdAt) {}
