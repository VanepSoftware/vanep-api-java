package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import java.time.LocalDate;

public record TripResponseDTO(
    String token,
    String driverToken,
    LocalDate serviceDate,
    Shift shift,
    TripStatus status,
    Instant startedAt,
    Instant finishedAt,
    boolean outsideWorkWindow,
    Instant createdAt,
    Instant updatedAt) {}
