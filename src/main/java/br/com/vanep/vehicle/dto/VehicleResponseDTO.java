package br.com.vanep.vehicle.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record VehicleResponseDTO(
    String token,
    String driverToken,
    String plate,
    String brand,
    String model,
    Integer manufactureYear,
    String color,
    Integer capacity,
    @Nullable String photoFrontUrl,
    @Nullable String photoSideUrl,
    boolean active,
    Instant createdAt) {}
