package br.com.vanep.clientday.dto;

import java.time.LocalDate;

/**
 * A driver of the client whose main van has no approved vehicle inspection still valid; {@code
 * expiredOn} is the latest expiry among the approved ones.
 */
public record ClientDayExpiredInspectionDTO(
    String driverToken, String driverName, LocalDate expiredOn) {}
