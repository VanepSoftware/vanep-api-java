package br.com.vanep.driver.dto;

import java.math.BigDecimal;
import java.util.List;

public record DriverSearchResponseDTO(
    String token,
    String name,
    String photo,
    BigDecimal rating,
    BigDecimal basePrice,
    Integer experienceYears,
    boolean available,
    List<String> serviceAreas) {
  public DriverSearchResponseDTO {
    serviceAreas = serviceAreas == null ? List.of() : List.copyOf(serviceAreas);
  }
}
