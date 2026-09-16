package br.com.vanep.cep.dto;

public record CepLookupResponseDTO(
    String cityToken, String cityName, String uf, String street, String neighborhood) {}
