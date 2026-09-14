package br.com.vanep.address.dto;

import jakarta.validation.constraints.Size;

public record AddressRequestDTO(
    @Size(max = 255, message = "{address.place_id.too_long}") String placeId,
    @Size(max = 255, message = "{address.session_token.too_long}") String sessionToken,
    @Size(max = 16, message = "{address.number.too_long}") String number,
    @Size(max = 128, message = "{address.complement.too_long}") String complement) {}
