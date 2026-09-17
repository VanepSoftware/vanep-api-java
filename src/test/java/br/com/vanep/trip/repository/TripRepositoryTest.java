package br.com.vanep.trip.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TripRepositoryTest {

  @Autowired private TripRepository repository;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);

  private DriverModel driver;

  @BeforeEach
  void setUp() {
    driver = createDriver("driver@vanep.com", "11144477735");
  }

  @Test
  void findsTodaysTripByDriverServiceDateAndShift() {
    TripModel saved = repository.save(newTrip(driver, TODAY, Shift.MORNING));

    assertThat(repository.findByDriverAndServiceDateAndShift(driver.getId(), TODAY, Shift.MORNING))
        .get()
        .extracting(trip -> trip.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    TripModel saved = repository.save(newTrip(driver, TODAY, Shift.MORNING));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findById(saved.getId()))
        .get()
        .extracting(trip -> trip.getToken())
        .isEqualTo(saved.getToken());
  }

  @Test
  void defaultsToScheduledWhenStatusIsNotSet() {
    TripModel saved = repository.save(newTrip(driver, TODAY, Shift.MORNING));

    assertThat(saved.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    assertThat(saved.getStartedAt()).isNull();
    assertThat(saved.getFinishedAt()).isNull();
  }

  @Test
  void returnsEmptyWhenTheDriverHasNoTripForThatShift() {
    repository.save(newTrip(driver, TODAY, Shift.MORNING));

    assertThat(
            repository.findByDriverAndServiceDateAndShift(driver.getId(), TODAY, Shift.AFTERNOON))
        .isEmpty();
  }

  @Test
  void doesNotReturnYesterdaysTripAsTodays() {
    repository.save(newTrip(driver, YESTERDAY, Shift.MORNING));

    assertThat(repository.findByDriverAndServiceDateAndShift(driver.getId(), TODAY, Shift.MORNING))
        .isEmpty();
    assertThat(repository.findByDriverAndServiceDate(driver.getId(), TODAY)).isEmpty();
  }

  @Test
  void keepsTwoShiftsOfTheSameDayAsSeparateTrips() {
    repository.save(newTrip(driver, TODAY, Shift.MORNING));
    repository.save(newTrip(driver, TODAY, Shift.AFTERNOON));

    List<TripModel> today = repository.findByDriverAndServiceDate(driver.getId(), TODAY);

    assertThat(today).hasSize(2);
    assertThat(today)
        .extracting(trip -> trip.getShift())
        .containsExactlyInAnyOrder(Shift.MORNING, Shift.AFTERNOON);
  }

  @Test
  void softDeletedTripIsAbsentFromDefaultQueries() {
    TripModel saved = repository.save(newTrip(driver, TODAY, Shift.MORNING));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByDriverAndServiceDate(driver.getId(), TODAY)).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void allowsANewTripAfterTheSameSlotWasSoftDeleted() {
    TripModel removed = repository.save(newTrip(driver, TODAY, Shift.MORNING));
    repository.delete(removed);

    TripModel recreated = repository.save(newTrip(driver, TODAY, Shift.MORNING));

    assertThat(recreated.getId()).isNotEqualTo(removed.getId());
    assertThat(repository.findByDriverAndServiceDateAndShift(driver.getId(), TODAY, Shift.MORNING))
        .get()
        .extracting(trip -> trip.getId())
        .isEqualTo(recreated.getId());
  }

  @Test
  void keepsTheSameShiftOfTwoDriversAsSeparateTrips() {
    DriverModel other = createDriver("other@vanep.com", "52998224725");

    repository.save(newTrip(driver, TODAY, Shift.MORNING));
    repository.save(newTrip(other, TODAY, Shift.MORNING));

    assertThat(repository.findByDriverAndServiceDate(driver.getId(), TODAY)).hasSize(1);
    assertThat(repository.findByDriverAndServiceDate(other.getId(), TODAY)).hasSize(1);
  }

  private TripModel newTrip(DriverModel owner, LocalDate serviceDate, Shift shift) {
    TripModel trip = new TripModel();
    trip.setDriver(owner);
    trip.setServiceDate(serviceDate);
    trip.setShift(shift);
    return trip;
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
}
