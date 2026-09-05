package br.com.vanep.location;

import br.com.vanep.location.dto.LocationComponentDTO;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.List;
import java.util.Optional;

public final class StreetAddressExtractor {
  private static final int SHALLOWEST_DISTRICT_DEPTH = 1;

  private static final String ROUTE = "route";
  private static final String STREET_NUMBER = "street_number";
  private static final String POSTAL_CODE = "postal_code";

  private StreetAddressExtractor() {}

  public static Optional<String> findStreet(PlaceDetailsResponseDTO details) {
    return findLongText(details, ROUTE).or(() -> findDeepestSubdivision(details));
  }

  private static Optional<String> findDeepestSubdivision(PlaceDetailsResponseDTO details) {
    List<LocationComponentDTO> districts =
        AddressComponentClassifier.districtsFromShallowToDeep(
            AddressComponentClassifier.classify(details.addressComponents()));
    if (districts.isEmpty()) {
      return Optional.empty();
    }
    LocationComponentDTO deepest = districts.get(districts.size() - 1);
    return deepest.depth() > SHALLOWEST_DISTRICT_DEPTH
        ? Optional.of(deepest.name())
        : Optional.empty();
  }

  public static Optional<String> findNumber(PlaceDetailsResponseDTO details) {
    return findLongText(details, STREET_NUMBER);
  }

  public static Optional<String> findZipCode(PlaceDetailsResponseDTO details) {
    return findLongText(details, POSTAL_CODE).map(zip -> zip.replaceAll("\\D", ""));
  }

  private static Optional<String> findLongText(PlaceDetailsResponseDTO details, String type) {
    return details.addressComponents().stream()
        .filter(component -> component.types().contains(type))
        .map(component -> component.longText())
        .filter(text -> text != null && !text.isBlank())
        .findFirst();
  }
}
