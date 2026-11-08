package br.com.vanep.driver.dto;

// The vehicle as a client sees it: no plate and no document photo.
public record DriverProfileVehicleResponseDTO(
    String token,
    String brand,
    String model,
    Integer manufactureYear,
    String color,
    Integer capacity,
    String photoFront,
    String photoSide) {}
