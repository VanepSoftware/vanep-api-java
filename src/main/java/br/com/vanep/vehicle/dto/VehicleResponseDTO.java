package br.com.vanep.vehicle.dto;

import java.time.Instant;

public record VehicleResponseDTO(
    String token,
    String driverToken,
    String plate,
    String brand,
    String model,
    Integer manufactureYear,
    String color,
    Integer capacity,
    String photoFrontUrl,
    String photoSideUrl,
    boolean active,
    Instant createdAt) {}
