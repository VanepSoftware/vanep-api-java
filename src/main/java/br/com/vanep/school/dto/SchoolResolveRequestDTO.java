package br.com.vanep.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolResolveRequestDTO(
    @NotBlank(message = "{school.place_id.required}")
        @Size(max = 255, message = "{school.place_id.too_long}")
        String placeId,
    @Size(max = 255, message = "{school.session_token.too_long}") String sessionToken) {}
