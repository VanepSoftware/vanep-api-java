package br.com.vanep.address.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonalAddressRequestDTO(
    @NotBlank(message = "{personal_address.city_token.required}") String cityToken,
    @NotBlank(message = "{personal_address.street.required}")
        @Size(max = 255, message = "{personal_address.street.too_long}")
        String street,
    @NotBlank(message = "{personal_address.zip_code.required}")
        @Pattern(regexp = "^\\d{8}$", message = "{personal_address.zip_code.invalid}")
        String zipCode,
    @Size(max = 16, message = "{personal_address.number.too_long}") String number,
    @Size(max = 128, message = "{personal_address.complement.too_long}") String complement,
    @Size(max = 128, message = "{personal_address.neighborhood.too_long}") String neighborhood) {}
