package br.com.vanep.drivercnh.dto;

import java.time.Instant;
import java.time.LocalDate;

public record DriverCnhResponseDTO(
    String token,
    String driverToken,
    String registrationNumber,
    String category,
    LocalDate issueDate,
    LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    String issuingState,
    String photoUrl,
    boolean active,
    Instant createdAt) {}
