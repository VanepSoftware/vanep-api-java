package br.com.vanep.assistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssistantInviteCreateRequestDTO(
    @NotBlank(message = "O e-mail é obrigatório.") @Email(message = "E-mail em formato inválido.")
        String email) {}
