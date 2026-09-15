package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.Shift;
import jakarta.validation.constraints.NotNull;

public record TripStartRequestDTO(@NotNull Shift shift) {}
