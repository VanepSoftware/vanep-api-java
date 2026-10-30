package br.com.vanep.location;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.places.dto.AddressComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreetAddressExtractorTest {

  @Test
  void findsTheFirstLevelSublocalityAsTheNeighborhood() {
    PlaceDetailsResponseDTO details =
        place(
            component("Rua Estela", "route"),
            component("Vila Mariana", "sublocality_level_1", "sublocality"),
            component("São Paulo", "administrative_area_level_2"));

    assertThat(StreetAddressExtractor.findNeighborhood(details)).contains("Vila Mariana");
  }

  @Test
  void findsTheShallowestSublocalityBelowAnAdministrativeRegion() {
    PlaceDetailsResponseDTO details =
        place(
            component("QI 21", "sublocality_level_3", "sublocality"),
            component("Taguatinga Norte", "sublocality_level_2", "sublocality"),
            component("Taguatinga", "administrative_area_level_4"),
            component("Brasília", "administrative_area_level_2"));

    assertThat(StreetAddressExtractor.findNeighborhood(details)).contains("Taguatinga Norte");
  }

  @Test
  void findsNoNeighborhoodWithoutASublocality() {
    PlaceDetailsResponseDTO details =
        place(
            component("Rua Estela", "route"),
            component("Taguatinga", "administrative_area_level_4"),
            component("São Paulo", "administrative_area_level_2"));

    assertThat(StreetAddressExtractor.findNeighborhood(details)).isEmpty();
  }

  private PlaceDetailsResponseDTO place(AddressComponentDTO... components) {
    return new PlaceDetailsResponseDTO("place-1", "formatted", List.of(components));
  }

  private AddressComponentDTO component(String text, String... types) {
    List<String> withPolitical = new ArrayList<>(List.of(types));
    withPolitical.add("political");
    return new AddressComponentDTO(text, text, withPolitical);
  }
}
