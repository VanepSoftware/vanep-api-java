package br.com.vanep.drivercnh.dto;

import java.time.LocalDate;

public record DriverCnhDataDTO(
    String registrationNumber,
    String category,
    LocalDate issueDate,
    LocalDate validUntil,
    LocalDate firstLicenseDate,
    String securityNumber,
    String issuingState) {}
