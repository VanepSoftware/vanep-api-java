package br.com.vanep.dependent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DependentRepositoryTest {

  private static final Set<ContractStatus> IN_PROGRESS =
      EnumSet.of(
          ContractStatus.AWAITING_SIGNATURES,
          ContractStatus.SIGNED,
          ContractStatus.ACTIVE,
          ContractStatus.SUSPENDED);

  @Autowired private DependentRepository dependents;
  @Autowired private ServiceRequestRepository requests;
  @Autowired private ContractRepository contracts;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private SchoolRepository schools;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;

  private CityModel city;
  private SchoolModel school;
  private ClientModel client;
  private ClientDriverModel link;
  private DependentModel lucas;
  private DependentModel ana;

  @BeforeEach
  void setUp() {
    city = createCity();
    school = createSchool();
    client = createClient("client@vanep.com", "10000000001");
    link = createLink(client, createDriver("driver@vanep.com", "11144477735"));
    lucas = createDependent(client, "Lucas Souza");
    ana = createDependent(client, "Ana Souza");
  }

  @Test
  void aDependentIsInAnOpenServiceRequestOnlyWhileItIsOpenOrAnswered() {
    ServiceRequestModel request = requests.save(newRequest(lucas));

    for (ServiceRequestStatus status : ServiceRequestStatus.values()) {
      jdbc.update(
          "update service_request set status = ? where id = ?", status.name(), request.getId());

      assertThat(dependents.existsInOpenServiceRequest(lucas.getId()))
          .as("request %s", status)
          .isEqualTo(ServiceRequestStatus.OPEN_STATUSES.contains(status));
    }
    assertThat(dependents.existsInOpenServiceRequest(ana.getId())).isFalse();
  }

  @Test
  void aDependentIsInAnOpenContractOnlyWhileTheContractIsInProgress() {
    ContractModel contract = contracts.save(newContract(lucas));

    for (ContractStatus status : ContractStatus.values()) {
      jdbc.update("update contract set status = ? where id = ?", status.name(), contract.getId());

      assertThat(dependents.existsInOpenContract(lucas.getId()))
          .as("contract %s", status)
          .isEqualTo(IN_PROGRESS.contains(status));
    }
    assertThat(dependents.existsInOpenContract(ana.getId())).isFalse();
  }

  @Test
  void aRemovedContractOrItemDoesNotLockTheDependent() {
    ContractModel contract = contracts.save(newContract(lucas));

    jdbc.update("update contract set deleted_at = now() where id = ?", contract.getId());
    assertThat(dependents.existsInOpenContract(lucas.getId())).isFalse();

    jdbc.update("update contract set deleted_at = null where id = ?", contract.getId());
    jdbc.update(
        "update contract_item set deleted_at = now() where contract_id = ?", contract.getId());
    assertThat(dependents.existsInOpenContract(lucas.getId())).isFalse();
  }

  @Test
  void findsTheTokensOfRemovedDependentsToo() {
    dependents.delete(lucas);

    assertThat(dependents.findTokensByIdIn(List.of(lucas.getId(), ana.getId())))
        .extracting(row -> row.getId(), row -> row.getToken())
        .containsExactlyInAnyOrder(
            tuple(lucas.getId(), lucas.getToken()), tuple(ana.getId(), ana.getToken()));
  }

  @Test
  void locksOnlyTheClientsActiveDependentsInIdOrder() {
    ClientModel other = createClient("other@vanep.com", "10000000002");
    DependentModel stranger = createDependent(other, "Bia Lima");
    DependentModel removed = createDependent(client, "Rafa Souza");
    dependents.delete(removed);

    List<DependentModel> locked =
        transactions.execute(
            status ->
                dependents.lockByTokenInAndClientId(
                    List.of(
                        ana.getToken(), stranger.getToken(), removed.getToken(), lucas.getToken()),
                    client.getId()));

    assertThat(locked)
        .extracting(dependent -> dependent.getName())
        .containsExactly("Lucas Souza", "Ana Souza");
  }

  @Test
  void locksTheActiveDependentsByIdInIdOrder() {
    DependentModel removed = createDependent(client, "Rafa Souza");
    dependents.delete(removed);

    List<DependentModel> locked =
        transactions.execute(
            status -> dependents.lockByIdIn(List.of(ana.getId(), removed.getId(), lucas.getId())));

    assertThat(locked)
        .extracting(dependent -> dependent.getName())
        .containsExactly("Lucas Souza", "Ana Souza");
  }

  private ServiceRequestModel newRequest(DependentModel dependent) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(DayOfWeek.MONDAY);
    day.setSchoolShift(SchoolShift.MORNING);
    day.setNeedsOutbound(true);
    day.setNeedsReturn(true);
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(dependent.getId());
    item.setSchoolId(school.getId());
    item.setPickupCityId(city.getId());
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent(dependent.getName(), null),
            new ServiceRequestItemModel.Snapshot.School(school.getName()),
            new ServiceRequestItemModel.Snapshot.Pickup(city.getName(), "SP", null)));
    item.addDay(day);
    ServiceRequestModel request = new ServiceRequestModel();
    request.setClientDriver(link);
    request.setStatus(ServiceRequestStatus.OPEN);
    request.setDesiredStartDate(LocalDate.of(2027, 2, 1));
    request.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
    request.addItem(item);
    return request;
  }

  private ContractModel newContract(DependentModel dependent) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(DayOfWeek.MONDAY);
    slot.setLeg(RouteLeg.OUTBOUND);
    slot.setShift(OperationShift.MORNING);
    slot.setWindowStart(LocalTime.of(6, 40));
    ScheduleModel schedule = new ScheduleModel();
    schedule.addSlot(slot);
    ContractItemModel item = new ContractItemModel();
    item.setDependent(dependent);
    item.setSchool(school);
    item.setPickupCity(city);
    item.setPickupStreet("Rua A");
    item.setMonthlyAmount(new BigDecimal("350.00"));
    item.setSchedule(schedule);
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStatus(ContractStatus.ACTIVE);
    contract.setStartsOn(LocalDate.of(2027, 2, 1));
    contract.setEndsOn(LocalDate.of(2027, 12, 1));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    contract.addItem(item);
    return contract;
  }

  private DependentModel createDependent(ClientModel owner, String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(owner.getId());
    dependent.setName(name);
    return dependents.save(dependent);
  }

  private ClientDriverModel createLink(ClientModel owner, DriverModel driver) {
    ClientDriverModel model = new ClientDriverModel();
    model.setClient(owner);
    model.setDriver(driver);
    model.setOrigin(LinkOrigin.CLIENT_SEARCH);
    return links.save(model);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel model = new ClientModel();
    model.setUser(createUser(UserType.CLIENT, email, document));
    return clients.save(model);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel model = new DriverModel();
    model.setUser(createUser(UserType.DRIVER, email, document));
    model.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(model);
  }

  private UserModel createUser(UserType type, String email, String document) {
    UserModel user = new UserModel();
    user.setType(type);
    user.setName("User");
    user.setEmail(email);
    user.setDocument(document);
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    return users.save(user);
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
}
