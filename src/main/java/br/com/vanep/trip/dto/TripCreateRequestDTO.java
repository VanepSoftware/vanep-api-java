package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.trip.enums.TripStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public record TripCreateRequestDTO(
    @NotBlank String driverToken,
    @NotNull LocalDate serviceDate,
    @NotNull OperationShift shift,
    TripStatus status,
    Instant startedAt,
    Instant finishedAt) {}
