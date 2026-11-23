package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TripResponseDTO(
    String token,
    @Nullable String driverToken,
    LocalDate serviceDate,
    OperationShift shift,
    TripStatus status,
    Instant startedAt,
    Instant finishedAt,
    boolean outsideWorkWindow,
    Instant createdAt,
    Instant updatedAt) {}
