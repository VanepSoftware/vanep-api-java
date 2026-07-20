package br.com.vanep.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDTO(
    @NotBlank(message = "A cidade do endereço é obrigatória.") String cityToken,
    @NotBlank(message = "O CEP é obrigatório.")
        @Pattern(regexp = "^\\d{8}$", message = "CEP em formato inválido (8 dígitos).")
        String zipCode,
    @NotBlank(message = "O logradouro é obrigatório.")
        @Size(max = 255, message = "O logradouro deve ter no máximo 255 caracteres.")
        String street,
    @Size(max = 16, message = "O número deve ter no máximo 16 caracteres.") String number,
    @Size(max = 128, message = "O complemento deve ter no máximo 128 caracteres.")
        String complement,
    @Size(max = 128, message = "O bairro deve ter no máximo 128 caracteres.") String district) {}
