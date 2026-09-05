package br.com.vanep.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonalAddressRequestDTO(
    @NotBlank @Size(max = 255) String placeId,
    @Size(max = 255) String sessionToken,
    @Size(max = 16) String number,
    @Size(max = 128) String complement) {}
