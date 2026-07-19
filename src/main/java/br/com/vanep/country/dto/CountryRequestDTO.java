package br.com.vanep.country.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CountryRequestDTO(
    @NotBlank(message = "O nome do país é obrigatório.")
        String name,
    @NotBlank(message = "O código ISO é obrigatório.")
        @Size(min = 2, max = 2, message = "O código ISO deve ter exatamente 2 caracteres.")
        String isoCode,
    @NotBlank(message = "O DDI (código telefônico) é obrigatório.")
        String phoneCode,
    @NotBlank(message = "A moeda é obrigatória.")
        @Size(min = 3, max = 3, message = "A moeda deve ter exatamente 3 caracteres.")
        String currency,
    String locale) {}
