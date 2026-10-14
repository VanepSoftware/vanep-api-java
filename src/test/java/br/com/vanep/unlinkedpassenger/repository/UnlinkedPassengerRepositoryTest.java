package br.com.vanep.unlinkedpassenger.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UnlinkedPassengerRepositoryTest {

  @Autowired private UnlinkedPassengerRepository passengers;
  @Autowired private ScheduleRepository schedules;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private SchoolRepository schools;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private DriverModel driver;
  private DriverModel otherDriver;
  private CityModel city;
  private SchoolModel school;

  @BeforeEach
  void setUp() {
    city = createCity();
    school = createSchool();
    driver = createDriver("driver@vanep.com", "11144477735");
    otherDriver = createDriver("other@vanep.com", "52998224725");
  }

  @Test
  void listsOnlyTheDriversOwnPassengersWithSchoolAddressAndSlotsLoaded() {
    passengers.save(newPassenger(driver, "Lucas", "Rua A"));
    passengers.save(newPassenger(otherDriver, "Ana", "Rua B"));

    List<UnlinkedPassengerModel> found = passengers.findByDriverId(driver.getId());

    assertThat(found).hasSize(1);
    UnlinkedPassengerModel lucas = found.getFirst();
    assertThat(lucas.getName()).isEqualTo("Lucas");
    assertThat(lucas.getSchoolShift()).isEqualTo(SchoolShift.FULLTIME);
    assertThat(lucas.getSchool().getName()).isEqualTo("Escola Municipal");
    assertThat(lucas.getAddress().getStreet()).isEqualTo("Rua A");
    assertThat(lucas.getSchedule().getSlots())
        .extracting(slot -> slot.getLeg())
        .containsExactlyInAnyOrder(RouteLeg.OUTBOUND, RouteLeg.RETURN);
  }

  @Test
  void findsAPassengerByTokenOnlyWithinItsDriver() {
    UnlinkedPassengerModel saved = passengers.save(newPassenger(driver, "Lucas", "Rua A"));

    assertThat(passengers.findByTokenAndDriverId(saved.getToken(), driver.getId()))
        .get()
        .extracting(passenger -> passenger.getSchedule().getSlots().size())
        .isEqualTo(2);
    assertThat(passengers.findByTokenAndDriverId(saved.getToken(), otherDriver.getId())).isEmpty();
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    UnlinkedPassengerModel saved = passengers.save(newPassenger(driver, "Lucas", "Rua A"));

    assertThat(saved.getToken()).isNotBlank().doesNotContain("-");
  }

  @Test
  void softDeleteHidesThePassengerAndItsScheduleButKeepsTheRows() {
    UnlinkedPassengerModel saved = passengers.save(newPassenger(driver, "Lucas", "Rua A"));
    Long scheduleId = saved.getSchedule().getId();

    passengers.delete(saved);

    assertThat(passengers.findByDriverId(driver.getId())).isEmpty();
    assertThat(passengers.findByTokenAndDriverId(saved.getToken(), driver.getId())).isEmpty();
    assertThat(schedules.findWithSlotsById(scheduleId)).isEmpty();
    assertThat(countSoftDeleted("unlinked_passenger")).isEqualTo(1);
    assertThat(countSoftDeleted("schedule")).isEqualTo(1);
    assertThat(countSoftDeleted("schedule_slot")).isEqualTo(2);
  }

  @Test
  void findsTheRoutePassengersOfTheDriverWithTheirPickupAddress() {
    UnlinkedPassengerModel lucas = passengers.save(newPassenger(driver, "Lucas", "Rua A"));
    passengers.save(newPassenger(otherDriver, "Ana", "Rua B"));

    List<RoutePassengerDTO> found =
        passengers.findRoutePassengers(driver.getId(), DayOfWeek.MONDAY, OperationShift.MORNING);

    assertThat(found)
        .singleElement()
        .satisfies(
            passenger -> {
              assertThat(passenger.source()).isEqualTo(PassengerSource.UNLINKED_PASSENGER);
              assertThat(passenger.token()).isEqualTo(lucas.getToken());
              assertThat(passenger.name()).isEqualTo("Lucas");
              assertThat(passenger.schoolName()).isEqualTo("Escola Municipal");
              assertThat(passenger.leg()).isEqualTo(RouteLeg.OUTBOUND);
              assertThat(passenger.windowStart()).isEqualTo(LocalTime.of(6, 40));
              assertThat(passenger.pickupStreet()).isEqualTo("Rua A");
              assertThat(passenger.pickupCityToken()).isEqualTo(city.getToken());
            });
  }

  @Test
  void anotherShiftOrWeekdayReturnsNoRoutePassenger() {
    passengers.save(newPassenger(driver, "Lucas", "Rua A"));

    assertThat(
            passengers.findRoutePassengers(driver.getId(), DayOfWeek.MONDAY, OperationShift.NIGHT))
        .isEmpty();
    assertThat(
            passengers.findRoutePassengers(
                driver.getId(), DayOfWeek.TUESDAY, OperationShift.MORNING))
        .isEmpty();
  }

  @Test
  void aDeletedPassengerIsNotARoutePassenger() {
    passengers.delete(passengers.save(newPassenger(driver, "Lucas", "Rua A")));

    assertThat(
            passengers.findRoutePassengers(
                driver.getId(), DayOfWeek.MONDAY, OperationShift.MORNING))
        .isEmpty();
  }

  @Test
  void findsTheRoutePassengersInOneStatementWhateverTheirNumber() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    passengers.save(newPassenger(driver, "Lucas", "Rua A"));
    long withOne = counter.countStatements(() -> findMondayMorningPassengers());

    passengers.save(newPassenger(driver, "Ana", "Rua B"));
    passengers.save(newPassenger(driver, "Bia", "Rua C"));
    long withThree = counter.countStatements(() -> findMondayMorningPassengers());

    assertThat(findMondayMorningPassengers()).hasSize(3);
    assertThat(withOne).isEqualTo(1);
    assertThat(withThree).isEqualTo(1);
  }

  private List<RoutePassengerDTO> findMondayMorningPassengers() {
    return passengers.findRoutePassengers(driver.getId(), DayOfWeek.MONDAY, OperationShift.MORNING);
  }

  private UnlinkedPassengerModel newPassenger(DriverModel owner, String name, String street) {
    UnlinkedPassengerModel passenger = new UnlinkedPassengerModel();
    passenger.setDriver(owner);
    passenger.setName(name);
    passenger.setSchool(school);
    passenger.setSchoolShift(SchoolShift.FULLTIME);
    passenger.setAddress(createAddress(street));
    passenger.setNotes("Sits in the front row");
    passenger.setSchedule(fullTimeMonday());
    return passenger;
  }

  private ScheduleModel fullTimeMonday() {
    ScheduleModel schedule = new ScheduleModel();
    schedule.addSlot(slot(RouteLeg.OUTBOUND, OperationShift.MORNING, LocalTime.of(6, 40)));
    schedule.addSlot(slot(RouteLeg.RETURN, OperationShift.AFTERNOON, LocalTime.of(17, 0)));
    return schedule;
  }

  private ScheduleSlotModel slot(RouteLeg leg, OperationShift shift, LocalTime windowStart) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(DayOfWeek.MONDAY);
    slot.setLeg(leg);
    slot.setShift(shift);
    slot.setWindowStart(windowStart);
    return slot;
  }

  private AddressModel createAddress(String street) {
    AddressModel address = new AddressModel();
    address.setCity(city);
    address.setStreet(street);
    address.setNumber("10");
    return addresses.save(address);
  }

  private SchoolModel createSchool() {
    SchoolModel model = new SchoolModel();
    model.setName("Escola Municipal");
    model.setCity(city);
    return schools.save(model);
  }

  private CityModel createCity() {
    CountryModel country = new CountryModel();
    country.setName("Brasil");
    country.setIsoCode("BR");
    country.setPhoneCode("+55");
    country.setCurrency("BRL");
    country.setLocale("pt-BR");
    country = countries.save(country);

    StateModel state = new StateModel();
    state.setName("São Paulo");
    state.setUf("SP");
    state.setCountry(country);
    state = states.save(state);

    CityModel model = new CityModel();
    model.setState(state);
    model.setName("Campinas");
    return cities.save(model);
  }

  private DriverModel createDriver(String email, String document) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Driver");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    user = users.save(user);

    DriverModel model = new DriverModel();
    model.setUser(user);
    model.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(model);
  }

  private Integer countSoftDeleted(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where deleted_at is not null", Integer.class);
  }
}
