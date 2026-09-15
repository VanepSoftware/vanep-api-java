package br.com.vanep.address.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.location.StreetAddressExtractor;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddressPlaceResolverService {
  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final MessageSource messages;

  public AddressPlaceResolverService(
      PlacesClient places, LocationResolverService resolver, MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.messages = messages;
  }

  public void applyPlace(
      AddressModel address, String placeId, String sessionToken, String number, String complement) {
    PlaceDetailsResponseDTO details = places.findPlaceDetails(placeId, sessionToken);

    String street =
        StreetAddressExtractor.findStreet(details)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, message("location.address.street_required")));

    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(details);

    address.setCity(chain.city());
    address.setDistrict(chain.deepestDistrict().orElse(null));
    address.setGooglePlaceId(details.id());
    address.setStreet(street);
    address.setZipCode(StreetAddressExtractor.findZipCode(details).orElse(null));
    address.setNumber(findNumber(details, number));
    address.setComplement(complement);
  }

  private String findNumber(PlaceDetailsResponseDTO details, String requestedNumber) {
    return Optional.ofNullable(requestedNumber)
        .filter(value -> !value.isBlank())
        .or(() -> StreetAddressExtractor.findNumber(details))
        .orElse(null);
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
