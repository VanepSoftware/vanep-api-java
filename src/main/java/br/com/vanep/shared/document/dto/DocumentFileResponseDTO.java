package br.com.vanep.shared.document.dto;

import org.jspecify.annotations.Nullable;

public record DocumentFileResponseDTO(
    String mimeType, Long sizeBytes, @Nullable String originalName) {}
