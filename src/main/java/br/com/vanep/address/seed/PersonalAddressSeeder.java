package br.com.vanep.address.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.school.seed.SchoolSeeder;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PersonalAddressSeeder {

  private static final Logger log = LoggerFactory.getLogger(PersonalAddressSeeder.class);
  private static final String SEED_CLIENT_EMAIL = "ana.souza@seed.vanep.com.br";

  private final UserRepository users;
  private final AddressRepository addresses;
  private final CityRepository cities;

  public PersonalAddressSeeder(
      UserRepository users, AddressRepository addresses, CityRepository cities) {
    this.users = users;
    this.addresses = addresses;
    this.cities = cities;
  }

  public void seed() {
    UserModel client = users.findByEmail(SEED_CLIENT_EMAIL).orElse(null);
    if (client == null || client.getAddressId() != null) {
      return;
    }
    Optional<CityModel> city = cities.findByIbgeCode(SchoolSeeder.SEED_CITY_IBGE_CODE);
    if (city.isEmpty()) {
      log.info(
          "Seed: personal address skipped; city not found (IBGE {}).",
          SchoolSeeder.SEED_CITY_IBGE_CODE);
      return;
    }
    client.setAddressId(addresses.save(home(city.get())).getId());
    users.save(client);
    log.info("Seed: personal address created ({}).", SEED_CLIENT_EMAIL);
  }

  private AddressModel home(CityModel city) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("01001000");
    address.setStreet("Rua das Acácias");
    address.setNumber("250");
    address.setNeighborhood("Centro");
    return address;
  }
}
