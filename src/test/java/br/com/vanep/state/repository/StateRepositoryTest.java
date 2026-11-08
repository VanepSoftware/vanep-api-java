package br.com.vanep.state.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StateRepositoryTest {
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private EntityManager entityManager;

  @Test
  void persistsAndReloadsStateWithoutGooglePlaceIdMapping() {
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
    StateModel saved = states.saveAndFlush(state);

    entityManager.clear();

    StateModel loaded = states.findById(saved.getId()).orElseThrow();

    assertThat(loaded.getUf()).isEqualTo("DF");
    assertThat(loaded.getName()).isEqualTo("Distrito Federal");
    assertThatThrownBy(() -> StateModel.class.getDeclaredField("googlePlaceId"))
        .isInstanceOf(NoSuchFieldException.class);
  }
}
