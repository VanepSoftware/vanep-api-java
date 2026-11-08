package br.com.vanep.address.dto;

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
    String countryIsoCode) {}
