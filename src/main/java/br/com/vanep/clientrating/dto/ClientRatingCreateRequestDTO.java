package br.com.vanep.clientrating.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ClientRatingCreateRequestDTO(
    @NotBlank(message = "{client_rating.clientToken.required}") String clientToken,
    @NotNull(message = "{client_rating.rating.required}")
        @DecimalMin(value = "1.00", message = "{client_rating.rating.min}")
        @DecimalMax(value = "5.00", message = "{client_rating.rating.max}")
        BigDecimal rating,
    String comment) {}
