package br.com.vanep.user.dto;

import br.com.vanep.user.UserProfileFieldLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserEmailChangeRequestDTO(
    @NotBlank(message = "{user.profile.email.required}")
        @Email(message = "{user.profile.email.invalid}")
        @Size(max = UserProfileFieldLimits.EMAIL_MAX, message = "{user.profile.email.too_long}")
        String email) {}
