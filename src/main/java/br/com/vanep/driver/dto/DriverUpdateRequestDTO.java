package br.com.vanep.driver.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record DriverUpdateRequestDTO(
    String photo,
    String bio,
    String cnpj,
    @Min(value = 0, message = "{driver.experienceYears.min}") Integer experienceYears,
    @NotBlank(message = "{driver.city.required}") String city,
    @NotNull(message = "{driver.basePrice.required}")
        @DecimalMin(value = "0.0", message = "{driver.basePrice.min}")
        BigDecimal basePrice,
    LocalTime workStartTime,
    LocalTime workEndTime,
    List<String> workDays,
    @Min(value = 0, message = "{driver.waitToleranceMinutes.min}") Integer waitToleranceMinutes,
    List<String> serviceAreas,
    @NotNull(message = "{driver.available.required}") Boolean available) {}
