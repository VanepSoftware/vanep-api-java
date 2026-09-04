package br.com.vanep.places.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Resposta do {@code Place Details} do Google, limitada aos campos do field mask que usamos.
 *
 * <p>O mask define o SKU cobrado: {@code id} sozinho cai em <i>Place Details Essentials IDs
 * Only</i> (gratuito), e {@code addressComponents} / {@code formattedAddress} elevam para <i>Place
 * Details Essentials</i>. Pedir qualquer campo Pro (por exemplo {@code displayName}) cobraria um
 * SKU adicional na mesma chamada — ver a Q2 e a Q6 do design.
 */
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

  /** Só vem preenchido quando o field mask pede {@code displayName} (SKU Pro — ver Q6). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DisplayName(String text, String languageCode) {}

  public String displayNameText() {
    return displayName == null ? null : displayName.text();
  }
}
