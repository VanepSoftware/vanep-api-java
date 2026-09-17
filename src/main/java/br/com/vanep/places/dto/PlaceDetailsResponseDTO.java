package br.com.vanep.places.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceDetailsResponseDTO(
    String id,
    String formattedAddress,
    List<AddressComponentDTO> addressComponents,
    List<String> types,
    DisplayName displayName) {
  public PlaceDetailsResponseDTO {
    addressComponents = addressComponents == null ? List.of() : List.copyOf(addressComponents);
    types = types == null ? List.of() : List.copyOf(types);
  }

  public PlaceDetailsResponseDTO(
      String id, String formattedAddress, List<AddressComponentDTO> addressComponents) {
    this(id, formattedAddress, addressComponents, List.of(), null);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DisplayName(String text, String languageCode) {}

  public String displayNameText() {
    return displayName == null ? null : displayName.text();
  }
}
