package br.com.vanep.address.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DependentAddressRequestDTO(
    @NotBlank(message = "{dependent_address.city_token.required}") String cityToken,
    @NotBlank(message = "{dependent_address.street.required}")
        @Size(max = 255, message = "{dependent_address.street.too_long}")
        String street,
    @NotBlank(message = "{dependent_address.zip_code.required}")
        @Pattern(regexp = "^\\d{8}$", message = "{dependent_address.zip_code.invalid}")
        String zipCode,
    @Size(max = 16, message = "{dependent_address.number.too_long}") String number,
    @Size(max = 128, message = "{dependent_address.complement.too_long}") String complement,
    @Size(max = 128, message = "{dependent_address.neighborhood.too_long}") String neighborhood) {}
