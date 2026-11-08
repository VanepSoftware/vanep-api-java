package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import java.time.LocalDate;

public record TripResponseDTO(
    String token,
    String driverToken,
    LocalDate serviceDate,
    OperationShift shift,
    TripStatus status,
    Instant startedAt,
    Instant finishedAt,
    boolean outsideWorkWindow,
    Instant createdAt,
    Instant updatedAt) {}
