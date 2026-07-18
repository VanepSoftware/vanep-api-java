package br.com.vanep.city.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CityRequestDTO(
    @NotBlank(message = "O nome da cidade é obrigatório.")
        @Size(max = 128, message = "O nome da cidade deve ter no máximo 128 caracteres.")
        String name,
    @NotBlank(message = "O estado da cidade é obrigatório.") String stateToken) {}
