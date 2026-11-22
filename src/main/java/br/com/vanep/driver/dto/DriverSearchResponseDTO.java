package br.com.vanep.driver.dto;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record DriverSearchResponseDTO(
    String token,
    String name,
    @Nullable String photo,
    @Nullable BigDecimal rating,
    BigDecimal basePrice,
    Integer experienceYears,
    boolean available,
    List<String> serviceAreas) {
  public DriverSearchResponseDTO {
    serviceAreas = serviceAreas == null ? List.of() : List.copyOf(serviceAreas);
  }
}
