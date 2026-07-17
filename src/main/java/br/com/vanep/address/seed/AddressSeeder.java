package br.com.vanep.address.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddressSeeder {

  private static final Logger log = LoggerFactory.getLogger(AddressSeeder.class);

  private record AddressSeed(
      String cityName, String zipCode, String street, String number, String district) {}

  private static final List<AddressSeed> SEEDS =
      List.of(
          new AddressSeed("São Paulo", "01310100", "Avenida Paulista", "1578", "Bela Vista"),
          new AddressSeed("Campinas", "13015904", "Rua Barão de Jaguara", "1481", "Centro"));

  private final AddressRepository addresses;
  private final CityRepository cities;

  public AddressSeeder(AddressRepository addresses, CityRepository cities) {
    this.addresses = addresses;
    this.cities = cities;
  }

  public void seed() {
    for (AddressSeed seed : SEEDS) {
      Optional<CityModel> city = cities.findFirstByNameIgnoreCase(seed.cityName());
      if (city.isEmpty()) {
        log.info("Seed: address skipped; city not found ({}).", seed.cityName());
        continue;
      }
      if (addresses.existsByZipCodeAndNumber(seed.zipCode(), seed.number())) {
        continue;
      }
      AddressModel address = new AddressModel();
      address.setCity(city.get());
      address.setZipCode(seed.zipCode());
      address.setStreet(seed.street());
      address.setNumber(seed.number());
      address.setDistrict(seed.district());
      addresses.save(address);
      log.info("Seed: address created ({}, {}).", seed.street(), seed.cityName());
    }
  }
}
