package br.com.vanep.seed;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.vanep.city.seed.CitySeeder;
import br.com.vanep.country.seed.CountrySeeder;
import br.com.vanep.state.seed.StateSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class GeographicDataSeederTest {
  @Mock private CountrySeeder countrySeeder;
  @Mock private StateSeeder stateSeeder;
  @Mock private CitySeeder citySeeder;

  private GeographicDataSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder = new GeographicDataSeeder(countrySeeder, stateSeeder, citySeeder);
  }

  @Test
  void seedsCountryThenStateThenCityWhenEnabled() {
    seeder.enabled = true;

    seeder.run(new DefaultApplicationArguments());

    var order = inOrder(countrySeeder, stateSeeder, citySeeder);
    order.verify(countrySeeder).seed();
    order.verify(stateSeeder).seed();
    order.verify(citySeeder).seed();
  }

  @Test
  void doesNothingWhenDisabled() {
    seeder.enabled = false;

    seeder.run(new DefaultApplicationArguments());

    verifyNoInteractions(countrySeeder, stateSeeder, citySeeder);
  }
}
