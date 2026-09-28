package br.com.vanep.seed;

import br.com.vanep.city.seed.CitySeeder;
import br.com.vanep.country.seed.CountrySeeder;
import br.com.vanep.state.seed.StateSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Country/state/city reference catalog, required in every environment for driver search, the city
 * picker and CEP lookup to work. Enabled by default and independent of {@code vanep.seed.enabled},
 * which only gates the demo admin/fake data in {@link DataSeeder} — so production never has to turn
 * on fake data just to get the catalog.
 */
@Component
public class GeographicDataSeeder implements ApplicationRunner {
  private final CountrySeeder countrySeeder;
  private final StateSeeder stateSeeder;
  private final CitySeeder citySeeder;

  @Value("${vanep.geographic-data.seed-enabled:true}")
  boolean enabled;

  public GeographicDataSeeder(
      CountrySeeder countrySeeder, StateSeeder stateSeeder, CitySeeder citySeeder) {
    this.countrySeeder = countrySeeder;
    this.stateSeeder = stateSeeder;
    this.citySeeder = citySeeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    countrySeeder.seed();
    stateSeeder.seed();
    citySeeder.seed();
  }
}
