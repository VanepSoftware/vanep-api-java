package br.com.vanep.drivercompliance.dto;

import java.time.LocalDate;

public record DriverComplianceCountsResponseDTO(
    long awaitingDecision,
    long awaitingDriver,
    long pendingDocuments,
    long expiring,
    long all,
    long badge,
    LocalDate asOf) {}
