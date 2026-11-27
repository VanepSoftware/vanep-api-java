package br.com.vanep.address.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.shared.error.ApiProblemException;
import org.springframework.stereotype.Service;

@Service
public class AddressCatalogResolverService {
  private final CityRepository cities;

  public AddressCatalogResolverService(CityRepository cities) {
    this.cities = cities;
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
        .orElseThrow(() -> ApiProblemException.notFound("city.not_found"));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
