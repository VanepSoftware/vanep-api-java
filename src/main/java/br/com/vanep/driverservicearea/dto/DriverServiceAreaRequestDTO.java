package br.com.vanep.driverservicearea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corpo de {@code PUT /api/drivers/me/service-areas}. Substitui o conjunto inteiro.
 *
 * <p>Cada item traz o seu próprio {@code sessionToken} porque cada caixa de autocomplete tem uma
 * sessão própria no Places (D5).
 */
public record DriverServiceAreaRequestDTO(@NotEmpty @Valid List<Item> areas) {

  public record Item(
      @NotBlank @Size(max = 255) String placeId, @Size(max = 255) String sessionToken) {}
}
