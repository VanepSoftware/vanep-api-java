package br.com.vanep.driverrating.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DriverRatingUpdateRequestDTO(
    @NotNull(message = "{driver_rating.rating.required}")
        @DecimalMin(value = "1.00", message = "{driver_rating.rating.min}")
        @DecimalMax(value = "5.00", message = "{driver_rating.rating.max}")
        BigDecimal rating,
    String comment) {}
