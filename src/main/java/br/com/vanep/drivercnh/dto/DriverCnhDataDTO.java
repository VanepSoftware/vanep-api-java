package br.com.vanep.drivercnh.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record DriverCnhDataDTO(
    @Nullable String registrationNumber,
    @Nullable String category,
    @Nullable LocalDate issueDate,
    @Nullable LocalDate validUntil,
    @Nullable LocalDate firstLicenseDate,
    @Nullable String securityNumber,
    @Nullable String issuingState) {}
