package br.com.vanep.address.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AddressRepositoryTest {

  @Autowired private AddressRepository repository;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private CityModel city;

  @BeforeEach
  void setUp() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel newCity = new CityModel();
    newCity.setState(state);
    newCity.setName("Campinas");
    city = cities.save(newCity);
  }

  private AddressModel newAddress(String street, String number) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setZipCode("13015904");
    address.setStreet(street);
    address.setNumber(number);
    return address;
  }

  @Test
  void generatesTokenOnPersistAndFindsByToken() {
    AddressModel saved = repository.save(newAddress("Rua Barão de Jaguara", "1481"));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void softDeletedAddressIsAbsentFromDefaultQueries() {
    AddressModel saved = repository.save(newAddress("Rua Removida", "10"));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  @Transactional
  void restoreByTokenBringsSoftDeletedAddressBack() {
    AddressModel saved = repository.saveAndFlush(newAddress("Rua Restaurada", "20"));
    String token = saved.getToken();
    repository.delete(saved);
    repository.flush();

    assertThat(repository.existsDeletedByToken(token)).isTrue();
    assertThat(repository.restoreByToken(token)).isEqualTo(1);
    assertThat(repository.existsDeletedByToken(token)).isFalse();
  }

  @Test
  void existsByZipCodeAndNumberMatchesActiveAddress() {
    repository.save(newAddress("Rua Barão de Jaguara", "1481"));

    assertThat(repository.existsByZipCodeAndNumber("13015904", "1481")).isTrue();
    assertThat(repository.existsByZipCodeAndNumber("13015904", "9999")).isFalse();
  }
}
