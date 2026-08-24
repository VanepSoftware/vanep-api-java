package br.com.vanep.driverservicearea.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * A query de contenção do D4, exercitada contra o H2.
 *
 * <p>O fato de estes testes rodarem é a evidência de que o desenho evitou PostGIS: são joins e
 * {@code IN} comuns. Se a decisão tivesse ido para geometria ou {@code ltree}, a suíte precisaria
 * de Testcontainers e a regra 25 da constituição cairia junto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DriverServiceAreaContainmentTest {

  @Autowired private DriverServiceAreaRepository areas;
  @Autowired private DistrictRepository districts;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private CityModel brasilia;
  private CityModel campinas;
  private DistrictModel taguatinga;
  private DistrictModel qnl5;
  private DistrictModel conjuntoJ;
  private DistrictModel aguasClaras;

  @BeforeEach
  void setUp() {
    CountryModel brasil = new CountryModel();
    brasil.setName("Brasil");
    brasil.setIsoCode("BR");
    brasil.setPhoneCode("+55");
    brasil.setCurrency("BRL");
    brasil = countries.save(brasil);

    StateModel df = new StateModel();
    df.setName("Distrito Federal");
    df.setUf("DF");
    df.setCountry(brasil);
    df = states.save(df);

    StateModel sp = new StateModel();
    sp.setName("São Paulo");
    sp.setUf("SP");
    sp.setCountry(brasil);
    sp = states.save(sp);

    brasilia = new CityModel();
    brasilia.setName("Brasília");
    brasilia.setState(df);
    brasilia = cities.save(brasilia);

    campinas = new CityModel();
    campinas.setName("Campinas");
    campinas.setState(sp);
    campinas = cities.save(campinas);

    taguatinga = saveDistrict("Taguatinga", null, brasilia);
    qnl5 = saveDistrict("QNL 5", taguatinga, brasilia);
    conjuntoJ = saveDistrict("Conjunto J", qnl5, brasilia);
    aguasClaras = saveDistrict("Águas Claras", null, brasilia);
  }

  private DistrictModel saveDistrict(String name, DistrictModel parent, CityModel city) {
    DistrictModel district = new DistrictModel();
    district.setName(name);
    district.setParent(parent);
    district.setCity(city);
    return districts.save(district);
  }

  private DriverModel saveDriver(String email) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Motorista " + email);
    user.setEmail(email);
    user.setDocument(String.valueOf(System.nanoTime()).substring(0, 11));
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel driver = new DriverModel();
    driver.setUser(user);
    driver.setBasePrice(BigDecimal.TEN);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return drivers.save(driver);
  }

  private void giveArea(DriverModel driver, CityModel city, DistrictModel district) {
    DriverServiceAreaModel area = new DriverServiceAreaModel();
    area.setDriver(driver);
    area.setCity(city);
    area.setDistrict(district);
    areas.save(area);
  }

  /** Ancestrais do ponto, do fundo para o raso — o que a busca passa para a query. */
  private List<Long> ancestorsOf(DistrictModel deepest) {
    List<Long> ids = new java.util.ArrayList<>();
    DistrictModel current = deepest;
    while (current != null) {
      ids.add(current.getId());
      current = current.getParent();
    }
    return ids;
  }

  @Test
  void ancestorDistrictCoversADeeperPoint() {
    DriverModel driver = saveDriver("taguatinga@vanep.com");
    giveArea(driver, brasilia, taguatinga);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(conjuntoJ)))
        .containsExactly(driver.getId());
  }

  @Test
  void wholeCityAreaCoversAnySpecificPoint() {
    DriverModel driver = saveDriver("cidade@vanep.com");
    giveArea(driver, brasilia, null);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(conjuntoJ)))
        .containsExactly(driver.getId());
  }

  @Test
  void exactDistrictMatches() {
    DriverModel driver = saveDriver("qnl5@vanep.com");
    giveArea(driver, brasilia, qnl5);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(qnl5)))
        .containsExactly(driver.getId());
  }

  @Test
  void siblingDistrictDoesNotMatch() {
    DriverModel driver = saveDriver("aguas@vanep.com");
    giveArea(driver, brasilia, aguasClaras);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(qnl5))).isEmpty();
  }

  /** O contrário do ancestral: quem declarou um ponto fundo não cobre a região inteira. */
  @Test
  void descendantDistrictDoesNotCoverAShallowerPoint() {
    DriverModel driver = saveDriver("conjunto@vanep.com");
    giveArea(driver, brasilia, conjuntoJ);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(taguatinga)))
        .isEmpty();
  }

  @Test
  void differentCityDoesNotMatch() {
    DriverModel driver = saveDriver("campinas@vanep.com");
    giveArea(driver, campinas, null);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(qnl5))).isEmpty();
  }

  /** Busca ampla por cidade devolve todo mundo, inclusive quem declarou só um distrito. */
  @Test
  void cityWideSearchReturnsDistrictLevelDriversToo() {
    DriverModel emTaguatinga = saveDriver("t@vanep.com");
    DriverModel emAguasClaras = saveDriver("a@vanep.com");
    DriverModel naCidadeInteira = saveDriver("c@vanep.com");
    giveArea(emTaguatinga, brasilia, taguatinga);
    giveArea(emAguasClaras, brasilia, aguasClaras);
    giveArea(naCidadeInteira, brasilia, null);

    assertThat(areas.findDriverIdsInCity(brasilia.getId()))
        .containsExactlyInAnyOrder(
            emTaguatinga.getId(), emAguasClaras.getId(), naCidadeInteira.getId());
  }

  @Test
  void doesNotDuplicateADriverWhoRegisteredSeveralCoveringAreas() {
    DriverModel driver = saveDriver("multi@vanep.com");
    giveArea(driver, brasilia, taguatinga);
    giveArea(driver, brasilia, qnl5);

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(conjuntoJ)))
        .containsExactly(driver.getId());
  }

  @Test
  void softDeletedAreaStopsCovering() {
    DriverModel driver = saveDriver("removido@vanep.com");
    giveArea(driver, brasilia, taguatinga);
    areas.deleteAll(areas.findByDriverId(driver.getId()));

    assertThat(areas.findDriverIdsCoveringPoint(brasilia.getId(), ancestorsOf(conjuntoJ)))
        .isEmpty();
  }
}
