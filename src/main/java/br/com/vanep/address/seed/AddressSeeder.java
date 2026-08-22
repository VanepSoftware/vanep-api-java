package br.com.vanep.address.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Optional;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddressSeeder {

  private static final Logger log = LoggerFactory.getLogger(AddressSeeder.class);
  private static final String SEED_CITY_NAME = "Campinas";
  private static final String SEED_ZIP_CODE = "13015904";
  private static final String SEED_STREET = "Rua Barão de Jaguara";
  private static final String SEED_NUMBER = "1481";
  private static final String SEED_DISTRICT = "Centro";

  private final AddressRepository addresses;
  private final CityRepository cities;
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final SchoolRepository schools;

  public AddressSeeder(
      AddressRepository addresses,
      CityRepository cities,
      ClientRepository clients,
      DependentRepository dependents,
      SchoolRepository schools) {
    this.addresses = addresses;
    this.cities = cities;
    this.clients = clients;
    this.dependents = dependents;
    this.schools = schools;
  }

  public void seed() {
    Optional<CityModel> city = cities.findFirstByNameIgnoreCase(SEED_CITY_NAME);
    if (city.isEmpty()) {
      log.info("Seed: address skipped; city not found ({}).", SEED_CITY_NAME);
      return;
    }
    for (ClientModel client : clients.findAll()) {
      linkIfMissing(
          client.getAddressId(),
          city.get(),
          savedId -> {
            client.setAddressId(savedId);
            clients.save(client);
          });
    }
    for (DependentModel dependent : dependents.findAll()) {
      linkIfMissing(
          dependent.getAddressId(),
          city.get(),
          savedId -> {
            dependent.setAddressId(savedId);
            dependents.save(dependent);
          });
    }
    for (SchoolModel school : schools.findAll()) {
      linkIfMissing(
          school.getAddressId(),
          city.get(),
          savedId -> {
            school.setAddressId(savedId);
            schools.save(school);
          });
    }
  }

  private void linkIfMissing(Long currentAddressId, CityModel city, LongConsumer persistOwnerLink) {
    if (currentAddressId != null) {
      return;
    }
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode(SEED_ZIP_CODE);
    address.setStreet(SEED_STREET);
    address.setNumber(SEED_NUMBER);
    address.setDistrict(SEED_DISTRICT);
    AddressModel saved = addresses.save(address);
    persistOwnerLink.accept(saved.getId());
    log.info("Seed: linked address created (id={}).", saved.getId());
  }
}
