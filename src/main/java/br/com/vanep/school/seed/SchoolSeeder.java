package br.com.vanep.school.seed;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchoolSeeder {

  private static final Logger log = LoggerFactory.getLogger(SchoolSeeder.class);
  public static final String SEED_SCHOOL_NAME = "Escola Municipal Seed";
  public static final String SEED_CITY_IBGE_CODE = "3550308";

  private final SchoolRepository schools;
  private final CityRepository cities;
  private final AddressRepository addresses;

  public SchoolSeeder(
      SchoolRepository schools, CityRepository cities, AddressRepository addresses) {
    this.schools = schools;
    this.cities = cities;
    this.addresses = addresses;
  }

  public void seed() {
    Optional<SchoolModel> existing = schools.findFirstByName(SEED_SCHOOL_NAME);
    if (existing.isPresent()) {
      addAddressWhenMissing(existing.get());
      return;
    }
    Optional<CityModel> city = cities.findByIbgeCode(SEED_CITY_IBGE_CODE);
    if (city.isEmpty()) {
      log.info("Seed: school seed skipped; city not found (IBGE {}).", SEED_CITY_IBGE_CODE);
      return;
    }
    SchoolModel school = new SchoolModel();
    school.setName(SEED_SCHOOL_NAME);
    school.setCity(city.get());
    school.setAddressId(addresses.save(campus(city.get())).getId());
    schools.save(school);
    log.info("Seed: school created ({}).", SEED_SCHOOL_NAME);
  }

  // Databases seeded before the school had an address get it on the next start.
  private void addAddressWhenMissing(SchoolModel school) {
    if (school.getAddressId() != null || school.getCity() == null) {
      return;
    }
    school.setAddressId(addresses.save(campus(school.getCity())).getId());
    schools.save(school);
    log.info("Seed: school address created ({}).", SEED_SCHOOL_NAME);
  }

  private AddressModel campus(CityModel city) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("01504001");
    address.setStreet("Rua Vergueiro");
    address.setNumber("1000");
    address.setNeighborhood("Liberdade");
    return address;
  }
}
