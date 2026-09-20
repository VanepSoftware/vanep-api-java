package br.com.vanep.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequestDTO(
    @NotBlank(message = "{auth.signup.email.required}")
        @Email(message = "{auth.signup.email.invalid}")
        String email,
    @NotBlank(message = "{auth.code.required}") String code) {}
