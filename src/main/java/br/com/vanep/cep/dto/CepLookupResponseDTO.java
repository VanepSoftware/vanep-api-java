package br.com.vanep.cep.dto;

import org.jspecify.annotations.Nullable;

public record CepLookupResponseDTO(
    String cityToken,
    String cityName,
    String uf,
    @Nullable String street,
    @Nullable String neighborhood) {}
