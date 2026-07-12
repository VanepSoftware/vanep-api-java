package br.com.vanep.driver.dto;

import java.time.Instant;

public record DriverLinkCodeGenerateResponseDTO(String code, Instant expiresAt) {}
