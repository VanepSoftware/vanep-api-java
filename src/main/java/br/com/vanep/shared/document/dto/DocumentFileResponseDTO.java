package br.com.vanep.shared.document.dto;

public record DocumentFileResponseDTO(String mimeType, Long sizeBytes, String originalName) {}
