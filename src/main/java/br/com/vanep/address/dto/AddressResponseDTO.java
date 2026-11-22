package br.com.vanep.address.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record AddressResponseDTO(
    String token,
    @Nullable String zipCode,
    String street,
    @Nullable String number,
    @Nullable String complement,
    @Nullable String neighborhood,
    String cityToken,
    String cityName,
    String stateUf,
    boolean active,
    Instant createdAt) {}
