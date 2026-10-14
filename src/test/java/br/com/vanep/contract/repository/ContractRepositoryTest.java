package br.com.vanep.contract.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.SqlStatementCounter;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContractRepositoryTest {

  private static final LocalDate STARTS_ON = LocalDate.of(2027, 2, 1);
  private static final LocalDate MONDAY_IN_TERM = LocalDate.of(2027, 3, 1);

  @Autowired private ContractRepository contracts;
  @Autowired private ContractItemRepository items;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private DependentRepository dependents;
  @Autowired private SchoolRepository schools;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private CityModel city;
  private ClientModel client;
  private SchoolModel school;
  private ClientDriverModel link;
  private DependentModel lucas;
  private DependentModel ana;

  @BeforeEach
  void setUp() {
    city = createCity();
    school = createSchool();
    client = createClient("client@vanep.com", "10000000001");
    link = createLink(client, createDriver("driver@vanep.com", "11144477735"));
    lucas = createDependent(client, "Lucas");
    ana = createDependent(client, "Ana");
  }

  @Test
  void savesAContractWithTwoItemsAndReadsTheItemsWithTheirSlots() {
    ContractModel contract =
        contracts.save(
            newContract(
                link,
                ContractStatus.ACTIVE,
                newItem(lucas, RouteLeg.OUTBOUND),
                newItem(ana, RouteLeg.OUTBOUND)));

    List<ContractItemModel> found = items.findByContractIdIn(List.of(contract.getId()));

    assertThat(found)
        .extracting(item -> item.getDependent().getName())
        .containsExactlyInAnyOrder("Lucas", "Ana");
    ContractItemModel first = found.getFirst();
    assertThat(first.getSchool().getName()).isEqualTo("Escola Municipal");
    assertThat(first.getPickupStreet()).isEqualTo("Rua A");
    assertThat(first.getPickupCity().getName()).isEqualTo("Campinas");
    assertThat(first.getMonthlyAmount()).isEqualByComparingTo("350.00");
    assertThat(first.getSchedule().getSlots())
        .extracting(slot -> slot.getLeg())
        .containsExactly(RouteLeg.OUTBOUND);
  }

  @Test
  void findsAContractByTokenWithTheLinkAndBothParties() {
    ContractModel saved =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    ContractModel found = contracts.findByToken(saved.getToken()).orElseThrow();

    assertThat(found.getClientDriver().getToken()).isEqualTo(link.getToken());
    assertThat(found.getClientDriver().getClient().getUser().getEmail())
        .isEqualTo("client@vanep.com");
    assertThat(found.getClientDriver().getDriver().getUser().getEmail())
        .isEqualTo("driver@vanep.com");
  }

  @Test
  void listsTheContractsOfALinkAndOnlyThatLink() {
    contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.OUTBOUND)));
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    ClientDriverModel otherLink =
        createLink(
            createClient("other@vanep.com", "20000000002"),
            createDriver("other-driver@vanep.com", "52998224725"));
    contracts.save(newContract(otherLink, ContractStatus.ACTIVE));

    assertThat(contracts.findByClientDriverId(link.getId()))
        .extracting(contract -> contract.getStatus())
        .containsExactlyInAnyOrder(ContractStatus.ENDED, ContractStatus.ACTIVE);
    assertThat(contracts.findStatusesByClientDriverId(link.getId()))
        .containsExactlyInAnyOrder(ContractStatus.ENDED, ContractStatus.ACTIVE);
  }

  @Test
  void findsTheActiveContractOfALinkOnlyWhenThereIsOne() {
    contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(contracts.findActiveByClientDriverId(link.getId())).isEmpty();

    ContractModel active =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(contracts.findActiveByClientDriverId(link.getId()))
        .get()
        .extracting(contract -> contract.getId())
        .isEqualTo(active.getId());
  }

  @Test
  void returnsTheSlotsOfTheDependentsSignedContractsNotEndedWithTheirSchedules() {
    ContractModel active =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    ClientDriverModel secondDriverLink =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    ContractModel suspended =
        contracts.save(
            newContract(
                secondDriverLink, ContractStatus.SUSPENDED, newItem(lucas, RouteLeg.RETURN)));
    ContractModel signed =
        contracts.save(newContract(link, ContractStatus.SIGNED, newItem(lucas, RouteLeg.RETURN)));
    contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.RETURN)));
    contracts.save(newContract(link, ContractStatus.CANCELLED, newItem(lucas, RouteLeg.OUTBOUND)));
    contracts.save(
        newContract(secondDriverLink, ContractStatus.ACTIVE, newItem(ana, RouteLeg.RETURN)));

    List<ScheduleSlotModel> slots =
        items.findSlotsByDependentIdAndContractStatusIn(
            lucas.getId(), ContractStatus.SIGNED_AND_NOT_ENDED);

    assertThat(slots)
        .extracting(slot -> slot.getSchedule().getId(), slot -> slot.getLeg())
        .containsExactlyInAnyOrder(
            tuple(scheduleIdOf(active), RouteLeg.OUTBOUND),
            tuple(scheduleIdOf(suspended), RouteLeg.RETURN),
            tuple(scheduleIdOf(signed), RouteLeg.RETURN));
  }

  @Test
  void pagesContractsWithTheirLinks() {
    contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.OUTBOUND)));
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    Page<ContractModel> page = contracts.findPage(PageRequest.of(0, 1));

    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().getFirst().getClientDriver().getToken())
        .isEqualTo(link.getToken());
  }

  @Test
  void generatesOpaqueTokensForTheContractAndItsItems() {
    ContractModel saved =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(saved.getToken()).isNotBlank().doesNotContain("-");
    assertThat(saved.getItems())
        .allSatisfy(item -> assertThat(item.getToken()).isNotBlank().doesNotContain("-"));
  }

  @Test
  void softDeleteHidesTheContractItsItemsAndSchedulesButKeepsTheRows() {
    ContractModel saved =
        contracts.save(
            newContract(
                link,
                ContractStatus.ACTIVE,
                newItem(lucas, RouteLeg.OUTBOUND),
                newItem(ana, RouteLeg.OUTBOUND)));

    contracts.delete(contracts.findByToken(saved.getToken()).orElseThrow());

    assertThat(contracts.findByToken(saved.getToken())).isEmpty();
    assertThat(contracts.findByClientDriverId(link.getId())).isEmpty();
    assertThat(items.findByContractIdIn(List.of(saved.getId()))).isEmpty();
    assertThat(
            items.findSlotsByDependentIdAndContractStatusIn(
                lucas.getId(), ContractStatus.SIGNED_AND_NOT_ENDED))
        .isEmpty();
    assertThat(countSoftDeleted("contract")).isEqualTo(1);
    assertThat(countSoftDeleted("contract_item")).isEqualTo(2);
    assertThat(countSoftDeleted("schedule")).isEqualTo(2);
    assertThat(countSoftDeleted("schedule_slot")).isEqualTo(2);
  }

  @Test
  void findsTheItemsOfSeveralContractsInOneQuery() {
    ContractModel ended =
        contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.OUTBOUND)));
    ContractModel active =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(ana, RouteLeg.RETURN)));

    assertThat(items.findByContractIdIn(List.of(ended.getId(), active.getId())))
        .extracting(item -> item.getContract().getId(), item -> item.getDependent().getName())
        .containsExactly(tuple(ended.getId(), "Lucas"), tuple(active.getId(), "Ana"));
  }

  @Test
  void findsTheLinkTokenOfAContract() {
    ContractModel saved =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(contracts.findClientDriverTokenByToken(saved.getToken())).contains(link.getToken());
    assertThat(contracts.findClientDriverTokenByToken("missing")).isEmpty();
  }

  @Test
  void restoringBringsBackTheContractItsItemsSchedulesAndSlots() {
    ContractModel saved =
        contracts.save(
            newContract(
                link,
                ContractStatus.ACTIVE,
                newItem(lucas, RouteLeg.OUTBOUND),
                newItem(ana, RouteLeg.OUTBOUND)));
    contracts.delete(contracts.findByToken(saved.getToken()).orElseThrow());
    assertThat(contracts.existsDeletedByToken(saved.getToken())).isTrue();

    transactions.executeWithoutResult(
        status -> {
          contracts.restoreByToken(saved.getToken());
          items.restoreByContractToken(saved.getToken());
          items.restoreSchedulesByContractToken(saved.getToken());
          items.restoreSlotsByContractToken(saved.getToken());
        });

    assertThat(contracts.existsDeletedByToken(saved.getToken())).isFalse();
    assertThat(items.findByContractIdIn(List.of(saved.getId())))
        .allSatisfy(item -> assertThat(item.getSchedule().getSlots()).hasSize(1))
        .hasSize(2);
    assertThat(countSoftDeleted("contract_item")).isZero();
    assertThat(countSoftDeleted("schedule")).isZero();
    assertThat(countSoftDeleted("schedule_slot")).isZero();
  }

  @Test
  void aDeletedActiveContractConflictsOnRestoreOnlyWithAnotherActiveOfTheSameLink() {
    ContractModel deletedActive =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    ContractModel deletedEnded =
        contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.OUTBOUND)));
    contracts.delete(contracts.findByToken(deletedActive.getToken()).orElseThrow());
    contracts.delete(contracts.findByToken(deletedEnded.getToken()).orElseThrow());

    assertThat(contracts.existsActiveConflictForRestore(deletedActive.getToken())).isFalse();

    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(contracts.existsActiveConflictForRestore(deletedActive.getToken())).isTrue();
    assertThat(contracts.existsActiveConflictForRestore(deletedEnded.getToken())).isFalse();
  }

  @Test
  void findsTheRoutePassengersOfTheDriversActiveContractWithTheCopiedPickup() {
    ContractModel active =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    String itemToken = active.getItems().iterator().next().getToken();

    List<RoutePassengerDTO> passengers = findMondayMorningPassengers(MONDAY_IN_TERM);

    assertThat(passengers)
        .singleElement()
        .satisfies(
            passenger -> {
              assertThat(passenger.source()).isEqualTo(PassengerSource.CONTRACT_ITEM);
              assertThat(passenger.token()).isEqualTo(itemToken);
              assertThat(passenger.name()).isEqualTo("Lucas");
              assertThat(passenger.schoolToken()).isEqualTo(school.getToken());
              assertThat(passenger.leg()).isEqualTo(RouteLeg.OUTBOUND);
              assertThat(passenger.windowStart()).isEqualTo(LocalTime.of(6, 40));
              assertThat(passenger.pickupStreet()).isEqualTo("Rua A");
              assertThat(passenger.pickupCityToken()).isEqualTo(city.getToken());
            });
  }

  @Test
  void aSuspendedContractPutsNobodyOnTheRoute() {
    contracts.save(newContract(link, ContractStatus.SUSPENDED, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(findMondayMorningPassengers(MONDAY_IN_TERM)).isEmpty();
  }

  @Test
  void anActiveContractOutsideItsPeriodPutsNobodyOnTheRoute() {
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(findMondayMorningPassengers(STARTS_ON.minusWeeks(1))).isEmpty();
    assertThat(findMondayMorningPassengers(LocalDate.of(2028, 1, 3))).isEmpty();
  }

  @Test
  void anotherShiftOrWeekdayReturnsNobody() {
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    assertThat(
            items.findRoutePassengers(
                driverId(), MONDAY_IN_TERM, DayOfWeek.MONDAY, OperationShift.AFTERNOON))
        .isEmpty();
    assertThat(
            items.findRoutePassengers(
                driverId(), MONDAY_IN_TERM.plusDays(1), DayOfWeek.TUESDAY, OperationShift.MORNING))
        .isEmpty();
  }

  @Test
  void aDeletedContractPutsNobodyOnTheRoute() {
    ContractModel active =
        contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));

    contracts.delete(active);

    assertThat(findMondayMorningPassengers(MONDAY_IN_TERM)).isEmpty();
  }

  @Test
  void anotherDriversContractsAreNotRoutePassengers() {
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    DriverModel otherDriver = createDriver("other@vanep.com", "52998224725");

    assertThat(
            items.findRoutePassengers(
                otherDriver.getId(), MONDAY_IN_TERM, DayOfWeek.MONDAY, OperationShift.MORNING))
        .isEmpty();
  }

  @Test
  void findsTheRoutePassengersInOneStatementWhateverTheirNumber() {
    SqlStatementCounter counter = new SqlStatementCounter(entityManagerFactory);
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    long withOne = counter.countStatements(() -> findMondayMorningPassengers(MONDAY_IN_TERM));

    for (int index = 2; index <= 4; index++) {
      ClientModel otherClient = createClient("client" + index + "@vanep.com", "2000000000" + index);
      ClientDriverModel otherLink = createLink(otherClient, link.getDriver());
      DependentModel dependent = createDependent(otherClient, "Dependent " + index);
      contracts.save(
          newContract(otherLink, ContractStatus.ACTIVE, newItem(dependent, RouteLeg.OUTBOUND)));
    }
    long withFour = counter.countStatements(() -> findMondayMorningPassengers(MONDAY_IN_TERM));

    assertThat(findMondayMorningPassengers(MONDAY_IN_TERM)).hasSize(4);
    assertThat(withOne).isEqualTo(1);
    assertThat(withFour).isEqualTo(1);
  }

  private List<RoutePassengerDTO> findMondayMorningPassengers(LocalDate serviceDate) {
    return items.findRoutePassengers(
        driverId(), serviceDate, DayOfWeek.MONDAY, OperationShift.MORNING);
  }

  private Long driverId() {
    return link.getDriver().getId();
  }

  private Long scheduleIdOf(ContractModel contract) {
    return contract.getItems().iterator().next().getSchedule().getId();
  }

  private ContractModel newContract(
      ClientDriverModel owner, ContractStatus status, ContractItemModel... contractItems) {
    ContractModel contract = new ContractModel();
    contract.setClientDriver(owner);
    contract.setStatus(status);
    contract.setStartsOn(STARTS_ON);
    contract.setEndsOn(STARTS_ON.plusMonths(10));
    contract.setTotalAmount(new BigDecimal("4200.00"));
    contract.setInstallments(12);
    contract.setDueDay(5);
    for (ContractItemModel item : contractItems) {
      contract.addItem(item);
    }
    return contract;
  }

  private ContractItemModel newItem(DependentModel dependent, RouteLeg leg) {
    ContractItemModel item = new ContractItemModel();
    item.setDependent(dependent);
    item.setSchool(school);
    item.setPickupCity(city);
    item.setPickupZipCode("13015904");
    item.setPickupStreet("Rua A");
    item.setPickupNumber("10");
    item.setMonthlyAmount(new BigDecimal("350.00"));
    item.setSchedule(mondaySchedule(leg));
    return item;
  }

  private ScheduleModel mondaySchedule(RouteLeg leg) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(DayOfWeek.MONDAY);
    slot.setLeg(leg);
    slot.setShift(leg == RouteLeg.OUTBOUND ? OperationShift.MORNING : OperationShift.AFTERNOON);
    slot.setWindowStart(leg == RouteLeg.OUTBOUND ? LocalTime.of(6, 40) : LocalTime.of(17, 0));
    ScheduleModel schedule = new ScheduleModel();
    schedule.addSlot(slot);
    return schedule;
  }

  private DependentModel createDependent(ClientModel client, String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(client.getId());
    dependent.setName(name);
    return dependents.save(dependent);
  }

  private ClientDriverModel createLink(ClientModel client, DriverModel driver) {
    ClientDriverModel model = new ClientDriverModel();
    model.setClient(client);
    model.setDriver(driver);
    return links.save(model);
  }

  private ClientModel createClient(String email, String document) {
    ClientModel client = new ClientModel();
    client.setUser(createUser(UserType.CLIENT, email, document));
    return clients.save(client);
  }

  private DriverModel createDriver(String email, String document) {
    DriverModel driver = new DriverModel();
    driver.setUser(createUser(UserType.DRIVER, email, document));
    driver.setBasePrice(new BigDecimal("100.00"));
    return drivers.save(driver);
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

  private Integer countSoftDeleted(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where deleted_at is not null", Integer.class);
  }
}
