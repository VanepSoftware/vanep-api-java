package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.OperationShift;
import jakarta.validation.constraints.NotNull;

public record TripStartRequestDTO(@NotNull OperationShift shift) {}
