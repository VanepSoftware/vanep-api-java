package br.com.vanep.address.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddressCatalogResolverService {
  private final CityRepository cities;
  private final MessageSource messages;

  public AddressCatalogResolverService(CityRepository cities, MessageSource messages) {
    this.cities = cities;
    this.messages = messages;
  }

  public void applyCity(
      AddressModel address,
      String cityToken,
      String street,
      String zipCode,
      String number,
      String complement,
      String neighborhood) {
    CityModel city = requireCityByToken(cityToken);

    address.setCity(city);
    address.setDistrict(null);
    address.setGooglePlaceId(null);
    address.setStreet(street);
    address.setZipCode(zipCode);
    address.setNumber(blankToNull(number));
    address.setComplement(blankToNull(complement));
    address.setNeighborhood(blankToNull(neighborhood));
  }

  private CityModel requireCityByToken(String cityToken) {
    return cities
        .findByToken(cityToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
