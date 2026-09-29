package br.com.vanep.driver.dto;

import java.math.BigDecimal;
import java.util.List;

// What any signed-in user may see of an approved driver: never CPF, CNPJ, email or address.
public record DriverProfileResponseDTO(
    String token,
    String name,
    String phone,
    String photo,
    BigDecimal rating,
    String bio,
    Integer experienceYears,
    BigDecimal basePrice,
    boolean available,
    List<String> serviceAreas,
    List<DriverProfileVehicleResponseDTO> vehicles) {
  public DriverProfileResponseDTO {
    serviceAreas = serviceAreas == null ? List.of() : List.copyOf(serviceAreas);
    vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
  }
}
