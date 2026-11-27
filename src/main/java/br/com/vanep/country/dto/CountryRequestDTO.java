package br.com.vanep.country.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CountryRequestDTO(
    @NotBlank(message = "{country.name.required}") String name,
    @NotBlank(message = "{country.iso_code.required}")
        @Size(min = 2, max = 2, message = "{country.iso_code.length}")
        String isoCode,
    @NotBlank(message = "{country.phone_code.required}") String phoneCode,
    @NotBlank(message = "{country.currency.required}")
        @Size(min = 3, max = 3, message = "{country.currency.length}")
        String currency,
    String locale) {}
