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
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ContractRepositoryTest {

  private static final LocalDate STARTS_ON = LocalDate.of(2027, 2, 1);

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

    List<ContractItemModel> found = items.findByContractId(contract.getId());

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
  void returnsOnlyTheSlotsOfTheDependentsActiveContracts() {
    contracts.save(newContract(link, ContractStatus.ACTIVE, newItem(lucas, RouteLeg.OUTBOUND)));
    contracts.save(newContract(link, ContractStatus.ENDED, newItem(lucas, RouteLeg.RETURN)));
    contracts.save(newContract(link, ContractStatus.SUSPENDED, newItem(lucas, RouteLeg.RETURN)));
    ClientDriverModel secondDriverLink =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    contracts.save(
        newContract(secondDriverLink, ContractStatus.ACTIVE, newItem(ana, RouteLeg.RETURN)));

    List<ScheduleSlotModel> slots = items.findActiveSlotsByDependentId(lucas.getId());

    assertThat(slots)
        .extracting(slot -> slot.getWeekday(), slot -> slot.getLeg())
        .containsExactly(tuple(DayOfWeek.MONDAY, RouteLeg.OUTBOUND));
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
    assertThat(items.findByContractId(saved.getId())).isEmpty();
    assertThat(items.findActiveSlotsByDependentId(lucas.getId())).isEmpty();
    assertThat(countSoftDeleted("contract")).isEqualTo(1);
    assertThat(countSoftDeleted("contract_item")).isEqualTo(2);
    assertThat(countSoftDeleted("schedule")).isEqualTo(2);
    assertThat(countSoftDeleted("schedule_slot")).isEqualTo(2);
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
