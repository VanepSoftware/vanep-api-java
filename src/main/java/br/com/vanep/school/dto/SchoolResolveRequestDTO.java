package br.com.vanep.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SchoolResolveRequestDTO(
    @NotBlank @Size(max = 255) String placeId, @Size(max = 255) String sessionToken) {}
