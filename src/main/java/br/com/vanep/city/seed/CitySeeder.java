package br.com.vanep.city.seed;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CitySeeder {

  private static final Logger log = LoggerFactory.getLogger(CitySeeder.class);

  private record CitySeed(String name, String uf) {}

  private static final List<CitySeed> SEEDS =
      List.of(
          new CitySeed("São Paulo", "SP"),
          new CitySeed("Campinas", "SP"),
          new CitySeed("Rio de Janeiro", "RJ"));

  private final CityRepository cities;
  private final StateRepository states;

  public CitySeeder(CityRepository cities, StateRepository states) {
    this.cities = cities;
    this.states = states;
  }

  public void seed() {
    for (CitySeed seed : SEEDS) {
      Optional<StateModel> state = states.findByUf(seed.uf());
      if (state.isEmpty()) {
        log.info("Seed: city skipped; state not found ({}).", seed.uf());
        continue;
      }
      if (cities.existsByNameIgnoreCaseAndStateId(seed.name(), state.get().getId())) {
        continue;
      }
      CityModel city = new CityModel();
      city.setState(state.get());
      city.setName(seed.name());
      cities.save(city);
      log.info("Seed: city created ({}/{}).", seed.name(), seed.uf());
    }
  }
}
