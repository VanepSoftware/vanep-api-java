package br.com.vanep.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/schools/resolve}. Só o {@code placeId} — nome, endereço e cidade vêm do
 * Google, nunca do cliente: a escola é recurso compartilhado, e um rótulo plantado apareceria para
 * todos os usuários.
 */
public record SchoolResolveRequestDTO(
    @NotBlank @Size(max = 255) String placeId, @Size(max = 255) String sessionToken) {}
