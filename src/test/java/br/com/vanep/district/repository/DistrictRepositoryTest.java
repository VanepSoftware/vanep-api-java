package br.com.vanep.district.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DistrictRepositoryTest {
  @Autowired private DistrictRepository repository;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private CityModel brasilia;

  @BeforeEach
  void setUp() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country = countries.save(country);

    StateModel distritoFederal = new StateModel();
    distritoFederal.setName("Distrito Federal");
    distritoFederal.setUf("DF");
    distritoFederal.setCountry(country);
    distritoFederal.setRequiresDistrict(true);
    distritoFederal = states.save(distritoFederal);

    CityModel city = new CityModel();
    city.setState(distritoFederal);
    city.setName("Brasília");
    brasilia = cities.save(city);
  }

  private DistrictModel newDistrict(String name, DistrictModel parent) {
    DistrictModel district = new DistrictModel();
    district.setCity(brasilia);
    district.setParent(parent);
    district.setName(name);
    return district;
  }

  @Test
  void persistsDistrictAsDirectChildOfCity() {
    DistrictModel saved = repository.save(newDistrict("Taguatinga", null));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getParent()).isNull();
    assertThat(saved.getCity().getId()).isEqualTo(brasilia.getId());
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void derivesNormalizedNameFromNameWithoutTheCallerSettingIt() {
    DistrictModel saved = repository.save(newDistrict("Águas Claras", null));

    assertThat(saved.getNormalizedName()).isEqualTo("aguas claras");
  }

  @Test
  void persistsNestedDistrictChain() {
    DistrictModel taguatinga = repository.save(newDistrict("Taguatinga", null));
    DistrictModel qnl5 = repository.save(newDistrict("QNL 5", taguatinga));
    DistrictModel conjuntoJ = repository.save(newDistrict("Conjunto J", qnl5));

    assertThat(conjuntoJ.getParent().getId()).isEqualTo(qnl5.getId());
    assertThat(qnl5.getParent().getId()).isEqualTo(taguatinga.getId());
    assertThat(taguatinga.getParent()).isNull();
  }

  @Test
  void findsDirectChildOfCityByNormalizedName() {
    repository.save(newDistrict("Taguatinga", null));

    assertThat(
            repository.findByCityIdAndParentIsNullAndNormalizedName(brasilia.getId(), "taguatinga"))
        .isPresent();
  }

  @Test
  void findsNestedChildByParentAndNormalizedName() {
    DistrictModel taguatinga = repository.save(newDistrict("Taguatinga", null));
    repository.save(newDistrict("QNL 5", taguatinga));

    assertThat(
            repository.findByCityIdAndParentIdAndNormalizedName(
                brasilia.getId(), taguatinga.getId(), "qnl 5"))
        .isPresent();
  }

  @Test
  void divergentSpellingOfTheSameRegionResolvesToTheSameNode() {
    DistrictModel saved = repository.save(newDistrict("Águas Claras", null));

    assertThat(
            repository.findByCityIdAndParentIsNullAndNormalizedName(
                brasilia.getId(), "aguas claras"))
        .get()
        .extracting(district -> district.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void rejectsDuplicateSiblingsUnderTheSameParent() {
    DistrictModel taguatinga = repository.save(newDistrict("Taguatinga", null));
    repository.save(newDistrict("QNL 5", taguatinga));

    assertThatThrownBy(() -> repository.saveAndFlush(newDistrict("QNL 5", taguatinga)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void allowsSameNameUnderDifferentParents() {
    DistrictModel taguatinga = repository.save(newDistrict("Taguatinga", null));
    DistrictModel ceilandia = repository.save(newDistrict("Ceilândia", null));

    repository.save(newDistrict("Setor Norte", taguatinga));
    DistrictModel sibling = repository.saveAndFlush(newDistrict("Setor Norte", ceilandia));

    assertThat(sibling.getId()).isNotNull();
  }

  @Test
  void softDeletedDistrictIsAbsentFromDefaultQueries() {
    DistrictModel saved = repository.save(newDistrict("Taguatinga", null));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(
            repository.findByCityIdAndParentIsNullAndNormalizedName(brasilia.getId(), "taguatinga"))
        .isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void listsEveryDistrictOfACity() {
    repository.save(newDistrict("Taguatinga", null));
    repository.save(newDistrict("Ceilândia", null));

    assertThat(repository.findByCityId(brasilia.getId())).hasSize(2);
  }
}
