package br.com.vanep.driver.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverLinkCodeConsumeRequestDTO(@NotBlank String code) {}
