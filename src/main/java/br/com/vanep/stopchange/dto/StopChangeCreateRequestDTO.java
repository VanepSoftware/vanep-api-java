package br.com.vanep.stopchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StopChangeCreateRequestDTO(
    @NotBlank(message = "O token do dependente é obrigatório.") String dependentToken,
    @NotBlank(message = "O token da rota é obrigatório.") String tripToken,
    String addressToken,
    String placeId,
    String sessionToken,
    String number,
    String complement,
    @Size(max = 255, message = "O motivo deve ter no máximo 255 caracteres.") String reason) {}
