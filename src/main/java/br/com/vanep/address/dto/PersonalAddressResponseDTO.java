package br.com.vanep.address.dto;

import org.jspecify.annotations.Nullable;

public record PersonalAddressResponseDTO(
    String token,
    String street,
    @Nullable String number,
    @Nullable String complement,
    @Nullable String zipCode,
    @Nullable String neighborhood,
    String cityName,
    String cityToken,
    String stateUf,
    String countryIsoCode) {}
