package br.com.vanep.school.seed;

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

  public SchoolSeeder(SchoolRepository schools, CityRepository cities) {
    this.schools = schools;
    this.cities = cities;
  }

  public void seed() {
    if (schools.findFirstByName(SEED_SCHOOL_NAME).isPresent()) {
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
    schools.save(school);
    log.info("Seed: school created ({}).", SEED_SCHOOL_NAME);
  }
}
