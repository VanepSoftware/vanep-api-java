package br.com.vanep.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserEmailChangeRequestDTO(
    @NotBlank(message = "{user.profile.email.required}")
        @Email(message = "{user.profile.email.invalid}")
        String email) {}
