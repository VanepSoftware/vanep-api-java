package br.com.vanep.stopchange.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
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
class StopChangeRequestRepositoryTest {

  @Autowired private StopChangeRequestRepository repository;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private ClientRepository clients;
  @Autowired private DependentRepository dependents;
  @Autowired private TripRepository trips;
  @Autowired private AddressRepository addresses;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

  private UserModel clientUser;
  private ClientModel client;
  private DependentModel dependent;
  private UserModel driverUser;
  private DriverModel driver;
  private TripModel trip;
  private AddressModel dropoffAddress;

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

    CityModel city = new CityModel();
    city.setState(state);
    city.setName("Campinas");
    city = cities.save(city);

    dropoffAddress = new AddressModel();
    dropoffAddress.setCity(city);
    dropoffAddress.setZipCode("13015904");
    dropoffAddress.setStreet("Rua da Vovó");
    dropoffAddress.setNumber("123");
    dropoffAddress = addresses.save(dropoffAddress);

    clientUser = createUser("client@vanep.com", "11144477735", UserType.CLIENT);
    client = new ClientModel();
    client.setUser(clientUser);
    client = clients.save(client);

    dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName("Filho do Cliente");
    dependent.setShift(Shift.MORNING);
    dependent = dependents.save(dependent);

    driverUser = createUser("driver@vanep.com", "22233344455", UserType.DRIVER);
    driver = new DriverModel();
    driver.setUser(driverUser);
    driver.setBasePrice(new BigDecimal("150.00"));
    driver = drivers.save(driver);

    trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(TODAY);
    trip.setShift(Shift.MORNING);
    trip = trips.save(trip);
  }

  @Test
  void generatesOpaqueTokenOnPersistAndDefaultsToPending() {
    StopChangeRequestModel req = createRequest(dependent, trip, clientUser, dropoffAddress);
    StopChangeRequestModel saved = repository.save(req);

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(saved.getStatus()).isEqualTo(StopChangeStatus.PENDING);
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void findsByTokenWithFetchedAssociations() {
    StopChangeRequestModel saved =
        repository.save(createRequest(dependent, trip, clientUser, dropoffAddress));

    StopChangeRequestModel found = repository.findByToken(saved.getToken()).orElseThrow();

    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getDependent().getName()).isEqualTo("Filho do Cliente");
    assertThat(found.getTrip().getId()).isEqualTo(trip.getId());
    assertThat(found.getRequestedByUser().getId()).isEqualTo(clientUser.getId());
    assertThat(found.getNewDropoffAddress().getStreet()).isEqualTo("Rua da Vovó");
  }

  @Test
  void findsByTripIdAndByTripIdAndStatus() {
    StopChangeRequestModel req = createRequest(dependent, trip, clientUser, dropoffAddress);
    repository.save(req);

    List<StopChangeRequestModel> allForTrip = repository.findByTripId(trip.getId());
    List<StopChangeRequestModel> pendingForTrip =
        repository.findByTripIdAndStatus(trip.getId(), StopChangeStatus.PENDING);
    List<StopChangeRequestModel> approvedForTrip =
        repository.findByTripIdAndStatus(trip.getId(), StopChangeStatus.APPROVED);

    assertThat(allForTrip).hasSize(1);
    assertThat(pendingForTrip).hasSize(1);
    assertThat(approvedForTrip).isEmpty();
  }

  @Test
  void findsByDependentIdAndTripIdAndStatus() {
    StopChangeRequestModel req = createRequest(dependent, trip, clientUser, dropoffAddress);
    repository.save(req);

    assertThat(
            repository.findByDependentIdAndTripIdAndStatus(
                dependent.getId(), trip.getId(), StopChangeStatus.PENDING))
        .isPresent();
    assertThat(
            repository.findByDependentIdAndTripIdAndStatus(
                dependent.getId(), trip.getId(), StopChangeStatus.APPROVED))
        .isEmpty();
  }

  @Test
  void findsByRequestedByUserIdAndServiceDate() {
    StopChangeRequestModel req = createRequest(dependent, trip, clientUser, dropoffAddress);
    repository.save(req);

    List<StopChangeRequestModel> results =
        repository.findByRequestedByUserIdAndServiceDate(clientUser.getId(), TODAY);

    assertThat(results).hasSize(1);
    assertThat(
            repository.findByRequestedByUserIdAndServiceDate(clientUser.getId(), TODAY.plusDays(1)))
        .isEmpty();
  }

  @Test
  void findsByDriverIdAndServiceDate() {
    StopChangeRequestModel req = createRequest(dependent, trip, clientUser, dropoffAddress);
    repository.save(req);

    List<StopChangeRequestModel> results =
        repository.findByDriverIdAndServiceDate(driver.getId(), TODAY);

    assertThat(results).hasSize(1);
  }

  @Test
  void softDeletedRequestIsExcludedFromDefaultQueries() {
    StopChangeRequestModel saved =
        repository.save(createRequest(dependent, trip, clientUser, dropoffAddress));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByTripId(trip.getId())).isEmpty();
    assertThat(repository.existsDeletedByToken(saved.getToken())).isTrue();
  }

  @Test
  void restoreByTokenRestoresEntity() {
    StopChangeRequestModel saved =
        repository.save(createRequest(dependent, trip, clientUser, dropoffAddress));
    repository.delete(saved);

    int restored = repository.restoreByToken(saved.getToken());

    assertThat(restored).isEqualTo(1);
    assertThat(repository.findByToken(saved.getToken())).isPresent();
    assertThat(repository.existsDeletedByToken(saved.getToken())).isFalse();
  }

  @Test
  void findsRequesterAndDriverUserTokensByRequestToken() {
    StopChangeRequestModel saved =
        repository.save(createRequest(dependent, trip, clientUser, dropoffAddress));

    assertThat(repository.findRequesterUserTokenByToken(saved.getToken()))
        .contains(clientUser.getToken());
    assertThat(repository.findDriverUserTokenByToken(saved.getToken()))
        .contains(driverUser.getToken());
  }

  private StopChangeRequestModel createRequest(
      DependentModel dep, TripModel t, UserModel requester, AddressModel address) {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setDependent(dep);
    req.setTrip(t);
    req.setServiceDate(t.getServiceDate());
    req.setRequestedByUser(requester);
    req.setNewDropoffAddress(address);
    req.setReason("Deixar na casa da avó hoje");
    return req;
  }

  private UserModel createUser(String email, String document, UserType type) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("User " + type);
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
  }
}
