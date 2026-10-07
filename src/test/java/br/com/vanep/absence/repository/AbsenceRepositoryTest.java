package br.com.vanep.absence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.enums.AbsenceSource;
import br.com.vanep.absence.model.AbsenceModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
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
class AbsenceRepositoryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);

  @Autowired private AbsenceRepository repository;
  @Autowired private ClientDriverRepository links;
  @Autowired private DependentRepository dependents;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private TripRepository trips;

  private ClientDriverModel link;
  private DependentModel dependent;

  @BeforeEach
  void setUp() {
    ClientModel client = createClient("maria@vanep.com", "11144477735");
    DriverModel driver = createDriver("carlos@vanep.com", "52998224725");
    link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link = links.save(link);
    dependent = createDependent(client, "Lucas");
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    AbsenceModel saved = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void findsByDependentDateAndLeg() {
    AbsenceModel saved = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    assertThat(
            repository.findByDependentAndDateAndLeg(dependent.getId(), TODAY, AbsenceLeg.OUTBOUND))
        .get()
        .extracting(absence -> absence.getId())
        .isEqualTo(saved.getId());
  }

  @Test
  void keepsOutboundAndReturnOnTheSameDayAsSeparateRows() {
    repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));
    repository.save(newAbsence(AbsenceLeg.RETURN, TODAY));

    List<AbsenceModel> today = repository.findByDependentAndDate(dependent.getId(), TODAY);

    assertThat(today).hasSize(2);
    assertThat(today)
        .extracting(absence -> absence.getLeg())
        .containsExactlyInAnyOrder(AbsenceLeg.OUTBOUND, AbsenceLeg.RETURN);
  }

  @Test
  void returnsEmptyWhenThatLegIsMissing() {
    repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    assertThat(repository.findByDependentAndDateAndLeg(dependent.getId(), TODAY, AbsenceLeg.RETURN))
        .isEmpty();
  }

  @Test
  void doesNotReturnYesterdaysAbsenceAsTodays() {
    repository.save(newAbsence(AbsenceLeg.OUTBOUND, YESTERDAY));

    assertThat(
            repository.findByDependentAndDateAndLeg(dependent.getId(), TODAY, AbsenceLeg.OUTBOUND))
        .isEmpty();
    assertThat(repository.findByDependentAndDate(dependent.getId(), TODAY)).isEmpty();
  }

  @Test
  void persistsWithoutATrip() {
    AbsenceModel saved = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    assertThat(saved.getTrip()).isNull();
    assertThat(repository.findByToken(saved.getToken()))
        .get()
        .extracting(absence -> absence.getTrip())
        .isNull();
  }

  @Test
  void attachesATripWhenOneIsSet() {
    TripModel trip = new TripModel();
    trip.setDriver(link.getDriver());
    trip.setServiceDate(TODAY);
    trip.setShift(Shift.MORNING);
    trip = trips.save(trip);

    AbsenceModel absence = newAbsence(AbsenceLeg.OUTBOUND, TODAY);
    absence.setTrip(trip);
    AbsenceModel saved = repository.save(absence);

    assertThat(repository.findByToken(saved.getToken()))
        .get()
        .extracting(found -> found.getTrip().getToken())
        .isEqualTo(trip.getToken());
  }

  @Test
  void softDeletedAbsenceIsAbsentFromDefaultQueries() {
    AbsenceModel saved = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(
            repository.findByDependentAndDateAndLeg(dependent.getId(), TODAY, AbsenceLeg.OUTBOUND))
        .isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }

  @Test
  void allowsANewAbsenceAfterTheSameLegWasSoftDeleted() {
    AbsenceModel removed = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));
    repository.delete(removed);

    AbsenceModel recreated = repository.save(newAbsence(AbsenceLeg.OUTBOUND, TODAY));

    assertThat(recreated.getId()).isNotEqualTo(removed.getId());
    assertThat(
            repository.findByDependentAndDateAndLeg(dependent.getId(), TODAY, AbsenceLeg.OUTBOUND))
        .get()
        .extracting(absence -> absence.getId())
        .isEqualTo(recreated.getId());
  }

  private AbsenceModel newAbsence(AbsenceLeg leg, LocalDate date) {
    AbsenceModel absence = new AbsenceModel();
    absence.setClientDriver(link);
    absence.setDependent(dependent);
    absence.setAbsenceDate(date);
    absence.setLeg(leg);
    absence.setSource(AbsenceSource.CLIENT);
    return absence;
  }

  private DependentModel createDependent(ClientModel client, String name) {
    DependentModel model = new DependentModel();
    model.setClientId(client.getId());
    model.setName(name);
    model.setShift(Shift.FULLTIME);
    return dependents.save(model);
  }

  private UserModel createUser(UserType type, String name, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName(name);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, "Client", email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, "Driver", email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
  }
}
