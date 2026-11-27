package br.com.vanep.assistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssistantInviteCreateRequestDTO(
    @NotBlank(message = "{assistant.invite.email.required}")
        @Email(message = "{assistant.invite.email.invalid}")
        String email) {}
