package br.com.vanep.address.dto;

import java.math.BigDecimal;

public record PersonalAddressResponseDTO(
    String token,
    String street,
    String number,
    String complement,
    String zipCode,
    String neighborhood,
    String cityName,
    String cityToken,
    String stateUf,
    BigDecimal cityLatitude,
    BigDecimal cityLongitude,
    String countryIsoCode) {}
