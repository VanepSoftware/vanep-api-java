package br.com.vanep.address.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.location.StreetAddressExtractor;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.shared.error.ApiProblemException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AddressPlaceResolverService {
  private final PlacesClient places;
  private final LocationResolverService resolver;

  public AddressPlaceResolverService(PlacesClient places, LocationResolverService resolver) {
    this.places = places;
    this.resolver = resolver;
  }

  public void applyPlace(
      AddressModel address, String placeId, String sessionToken, String number, String complement) {
    PlaceDetailsResponseDTO details = places.findPlaceDetails(placeId, sessionToken);

    String street =
        StreetAddressExtractor.findStreet(details)
            .orElseThrow(() -> ApiProblemException.badRequest("location.address.street_required"));

    writePlace(
        address, details, resolver.resolveAndPersist(details).city(), street, number, complement);
  }

  // The school resolve already holds the place details: building from them avoids a second paid
  // Place Details call, and a place without a street yields no address instead of an error.
  public Optional<AddressModel> buildFromPlace(PlaceDetailsResponseDTO details, CityModel city) {
    return StreetAddressExtractor.findStreet(details)
        .map(
            street -> {
              AddressModel address = new AddressModel();
              writePlace(address, details, city, street, null, null);
              return address;
            });
  }

  void writePlace(
      AddressModel address,
      PlaceDetailsResponseDTO details,
      CityModel city,
      String street,
      String number,
      String complement) {
    address.setCity(city);
    address.setGooglePlaceId(details.id());
    address.setStreet(street);
    address.setZipCode(StreetAddressExtractor.findZipCode(details).orElse(null));
    address.setNumber(findNumber(details, number));
    address.setComplement(complement);
    address.setNeighborhood(StreetAddressExtractor.findNeighborhood(details).orElse(null));
  }

  private String findNumber(PlaceDetailsResponseDTO details, String requestedNumber) {
    return Optional.ofNullable(requestedNumber)
        .filter(value -> !value.isBlank())
        .or(() -> StreetAddressExtractor.findNumber(details))
        .orElse(null);
  }
}
