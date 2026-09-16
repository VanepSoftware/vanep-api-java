package br.com.vanep.city.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CityRepositoryTest {
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private EntityManager entityManager;

  private StateModel distritoFederal;

  @BeforeEach
  void setUp() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("Distrito Federal");
    state.setUf("DF");
    state.setCountry(country);
    distritoFederal = states.save(state);
  }

  private CityModel newCity(String name, String ibgeCode) {
    CityModel city = new CityModel();
    city.setState(distritoFederal);
    city.setName(name);
    city.setIbgeCode(ibgeCode);
    return city;
  }

  @Test
  void persistsAndReloadsIbgeCode() {
    CityModel saved = cities.saveAndFlush(newCity("Brasília", "5300108"));

    entityManager.clear();

    CityModel loaded = cities.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getIbgeCode()).isEqualTo("5300108");
    assertThat(loaded.getName()).isEqualTo("Brasília");
  }

  @Test
  void rejectsDuplicateIbgeCodeAmongActiveCities() {
    cities.saveAndFlush(newCity("Brasília", "5300108"));

    assertThatThrownBy(() -> cities.saveAndFlush(newCity("Gama", "5300108")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void cityModelHasNoGooglePlaceIdMapping() {
    assertThatThrownBy(() -> CityModel.class.getDeclaredField("googlePlaceId"))
        .isInstanceOf(NoSuchFieldException.class);
  }

  @Test
  void findsCitiesByStateAndNormalizedNameContaining() {
    cities.saveAndFlush(newCity("Brasília", "5300108"));
    cities.saveAndFlush(newCity("Gama", "5300109"));

    Page<CityModel> page =
        cities.findByStateIdAndNormalizedNameContaining(
            distritoFederal.getId(), "brasil", Pageable.unpaged());

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().getFirst().getName()).isEqualTo("Brasília");
  }

  @Test
  void doesNotLookupCityByGooglePlaceId() {
    boolean looksUpByGooglePlaceId =
        Arrays.stream(CityRepository.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("findByGooglePlaceId"));

    assertThat(looksUpByGooglePlaceId).isFalse();
  }
}
