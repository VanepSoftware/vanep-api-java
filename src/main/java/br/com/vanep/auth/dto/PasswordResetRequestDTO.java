package br.com.vanep.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDTO(
    @NotBlank(message = "{auth.signup.email.required}")
        @Email(message = "{auth.signup.email.invalid}")
        String email,
    @NotBlank(message = "{auth.code.required}") String code,
    // Same minimum the web reset form enforces; sign-up keeps its own 6.
    @NotBlank(message = "{auth.signup.password.required}")
        @Size(min = 8, message = "{auth.password.reset.min}")
        String newPassword) {}
