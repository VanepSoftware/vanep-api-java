package br.com.vanep.assistant.dto;

import br.com.vanep.shared.enums.OperationShift;
import java.time.LocalTime;

/**
 * One shift the assistant works with the linked driver, shown on the driver's main vehicle.
 * Contracts and trips belong to a driver and a shift, not to a vehicle, so the numbers are per
 * shift: {@code studentCount} is the active roster of the shift, {@code departureTime} is the first
 * pickup of the shift today and {@code hasRouteToday} tells whether the shift runs today.
 */
public record AssistantVanResponseDTO(
    String token,
    String driverToken,
    String driverName,
    String plate,
    String model,
    OperationShift shift,
    LocalTime departureTime,
    long studentCount,
    boolean hasRouteToday) {}
