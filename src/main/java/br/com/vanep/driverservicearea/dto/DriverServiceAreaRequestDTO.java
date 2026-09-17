package br.com.vanep.driverservicearea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DriverServiceAreaRequestDTO(
    @NotEmpty @Size(max = MAX_AREAS, message = "{driver_service_area.areas.too_many}") @Valid
        List<Item> areas) {
  public static final int MAX_AREAS = 10;

  public record Item(
      @Size(max = 255) String placeId,
      @Size(max = 32) String areaToken,
      @Size(max = 255) String sessionToken) {
    public boolean isExistingArea() {
      return areaToken != null && !areaToken.isBlank();
    }

    @AssertTrue(message = "{driver_service_area.item.invalid}")
    public boolean isExactlyOneIdentifier() {
      boolean hasPlace = placeId != null && !placeId.isBlank();
      return hasPlace ^ isExistingArea();
    }
  }
}
