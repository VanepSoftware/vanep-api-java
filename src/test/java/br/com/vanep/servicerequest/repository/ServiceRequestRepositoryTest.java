package br.com.vanep.servicerequest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
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
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ServiceRequestRepositoryTest {

  private static final LocalDate DESIRED_START = LocalDate.of(2027, 2, 1);
  private static final LocalDate LUCAS_BIRTH_DATE = LocalDate.of(2016, 4, 2);
  private static final LocalDate ANA_BIRTH_DATE = LocalDate.of(2019, 8, 15);
  private static final Set<ServiceRequestStatus> ANY_STATUS =
      EnumSet.allOf(ServiceRequestStatus.class);

  @Autowired private ServiceRequestRepository requests;
  @Autowired private ClientDriverRepository links;
  @Autowired private ClientRepository clients;
  @Autowired private DriverRepository drivers;
  @Autowired private UserRepository users;
  @Autowired private DependentRepository dependents;
  @Autowired private SchoolRepository schools;
  @Autowired private CityRepository cities;
  @Autowired private StateRepository states;
  @Autowired private CountryRepository countries;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  private CityModel city;
  private SchoolModel school;
  private ClientModel client;
  private DriverModel driver;
  private ClientDriverModel link;
  private DependentModel lucas;
  private DependentModel ana;

  @BeforeEach
  void setUp() {
    city = createCity();
    school = createSchool();
    client = createClient("client@vanep.com", "10000000001");
    driver = createDriver("driver@vanep.com", "11144477735");
    link = createLink(client, driver);
    lucas = createDependent(client, "Lucas Souza", LUCAS_BIRTH_DATE);
    ana = createDependent(client, "Ana Souza", ANA_BIRTH_DATE);
  }

  @Test
  void rereadsARequestOutsideTheTransactionWithItsItemsCopiesAndDays() {
    ServiceRequestModel saved =
        requests.save(
            newRequest(
                link,
                ServiceRequestStatus.OPEN,
                newItem(
                    lucas,
                    newDay(DayOfWeek.MONDAY, SchoolShift.MORNING, true, true),
                    newDay(DayOfWeek.TUESDAY, SchoolShift.FULLTIME, true, true),
                    newDay(DayOfWeek.WEDNESDAY, SchoolShift.MORNING, true, false)),
                newItem(ana, newDay(DayOfWeek.MONDAY, SchoolShift.AFTERNOON, true, true))));

    ServiceRequestModel found =
        requests.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();

    assertThat(found.getClientDriver().getClient().getUser().getEmail())
        .isEqualTo("client@vanep.com");
    assertThat(found.getItems())
        .extracting(
            item -> item.getSnapshot().dependent().name(),
            item -> item.getSnapshot().dependent().birthDate(),
            item -> item.getSnapshot().school().name(),
            item -> item.getSnapshot().pickup().cityName(),
            item -> item.getSnapshot().pickup().stateUf(),
            item -> item.getSnapshot().pickup().neighborhood())
        .containsExactlyInAnyOrder(
            tuple("Lucas Souza", LUCAS_BIRTH_DATE, "Escola Municipal", "Campinas", "SP", "Cambuí"),
            tuple("Ana Souza", ANA_BIRTH_DATE, "Escola Municipal", "Campinas", "SP", "Cambuí"));
    assertThat(found.getItems())
        .extracting(item -> item.getSchoolId(), item -> item.getPickupCityId())
        .containsOnly(tuple(school.getId(), city.getId()));
    assertThat(itemOf(found, lucas).getDays())
        .extracting(
            day -> day.getWeekday(),
            day -> day.getSchoolShift(),
            day -> day.isNeedsOutbound(),
            day -> day.isNeedsReturn())
        .containsExactlyInAnyOrder(
            tuple(DayOfWeek.MONDAY, SchoolShift.MORNING, true, true),
            tuple(DayOfWeek.TUESDAY, SchoolShift.FULLTIME, true, true),
            tuple(DayOfWeek.WEDNESDAY, SchoolShift.MORNING, true, false));
    assertThat(itemOf(found, ana).getDays())
        .extracting(day -> day.getSchoolShift())
        .containsExactly(SchoolShift.AFTERNOON);
  }

  @Test
  void aClosedRequestKeepsBothItemsAfterOneSiblingIsRemoved() {
    ServiceRequestModel saved =
        requests.save(
            newRequest(
                link,
                ServiceRequestStatus.WITHDRAWN,
                newItem(lucas, mondayRoundTrip()),
                newItem(ana, mondayRoundTrip())));

    dependents.delete(dependents.findById(lucas.getId()).orElseThrow());

    assertThat(requests.findByTokenAndClientId(saved.getToken(), client.getId()))
        .get()
        .satisfies(
            found ->
                assertThat(found.getItems())
                    .extracting(item -> item.getSnapshot().dependent().name())
                    .containsExactlyInAnyOrder("Lucas Souza", "Ana Souza"));
    assertThat(requests.findByTokenAndDriverId(saved.getToken(), driver.getId())).isPresent();
    assertThat(
            requests.findPageByClientIdAndStatusIn(
                client.getId(), ANY_STATUS, PageRequest.of(0, 10)))
        .extracting(request -> request.getToken())
        .containsExactly(saved.getToken());
    assertThat(requests.findWithItemsByIdIn(List.of(saved.getId())))
        .singleElement()
        .satisfies(found -> assertThat(found.getItems()).hasSize(2));
  }

  @Test
  void theSnapshotOutlivesEditsAndRemovalsOfTheDependentAndSchool() {
    ServiceRequestModel saved =
        requests.save(
            newRequest(link, ServiceRequestStatus.EXPIRED, newItem(lucas, mondayRoundTrip())));
    ServiceRequestItemModel.Snapshot sent = saved.getItems().iterator().next().getSnapshot();

    DependentModel renamed = dependents.findById(lucas.getId()).orElseThrow();
    renamed.setName("Lucas Gabriel");
    renamed.setBirthDate(LocalDate.of(2015, 1, 1));
    dependents.save(renamed);
    dependents.delete(dependents.findById(lucas.getId()).orElseThrow());
    transactions.executeWithoutResult(status -> dependents.restoreByToken(lucas.getToken()));
    dependents.delete(dependents.findById(lucas.getId()).orElseThrow());
    schools.delete(schools.findById(school.getId()).orElseThrow());

    assertThat(requests.findByTokenAndClientId(saved.getToken(), client.getId()))
        .get()
        .satisfies(
            found ->
                assertThat(found.getItems())
                    .singleElement()
                    .satisfies(item -> assertThat(item.getSnapshot()).isEqualTo(sent)));
    assertThat(sent.dependent().name()).isEqualTo("Lucas Souza");
  }

  @Test
  void storesTheSnapshotDatesAsIsoText() {
    requests.save(newRequest(link, ServiceRequestStatus.OPEN, newItem(lucas, mondayRoundTrip())));

    assertThat(
            jdbc.queryForObject(
                "select cast(snapshot as varchar) from service_request_item", String.class))
        .contains("\"birthDate\":\"2016-04-02\"");
  }

  @Test
  void anItemWithoutNeighborhoodIsStillRead() {
    ServiceRequestItemModel item = newItemIn(null, lucas, mondayRoundTrip());
    ServiceRequestModel saved = requests.save(newRequest(link, ServiceRequestStatus.OPEN, item));

    ServiceRequestModel found =
        requests.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();

    assertThat(found.getItems())
        .singleElement()
        .satisfies(read -> assertThat(read.getSnapshot().pickup().neighborhood()).isNull());
  }

  @Test
  void findsByTokenOnlyWithinTheClient() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);
    ClientModel other = createClient("other@vanep.com", "20000000002");

    assertThat(requests.findByTokenAndClientId(saved.getToken(), client.getId())).isPresent();
    assertThat(requests.findByTokenAndClientId(saved.getToken(), other.getId())).isEmpty();
    assertThat(requests.findByTokenAndClientId("missing", client.getId())).isEmpty();
  }

  @Test
  void findsByTokenOnlyWithinTheDriverWithTheDays() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);
    DriverModel other = createDriver("other-driver@vanep.com", "52998224725");

    ServiceRequestModel found =
        requests.findByTokenAndDriverId(saved.getToken(), driver.getId()).orElseThrow();

    assertThat(found.getItems())
        .singleElement()
        .satisfies(item -> assertThat(item.getDays()).hasSize(1));
    assertThat(requests.findByTokenAndDriverId(saved.getToken(), other.getId())).isEmpty();
  }

  @Test
  void pagesTheRequestsOfAClientFilteredByStatus() {
    ServiceRequestModel expired = saveRequest(link, ServiceRequestStatus.EXPIRED);
    ServiceRequestModel open = saveRequest(link, ServiceRequestStatus.OPEN);
    ClientDriverModel secondDriverLink =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    ServiceRequestModel declined = saveRequest(secondDriverLink, ServiceRequestStatus.DECLINED);
    ClientModel other = createClient("other@vanep.com", "20000000002");
    saveRequest(createLink(other, driver), ServiceRequestStatus.OPEN);

    Page<ServiceRequestModel> all =
        requests.findPageByClientIdAndStatusIn(client.getId(), ANY_STATUS, PageRequest.of(0, 10));
    Page<ServiceRequestModel> openOnly =
        requests.findPageByClientIdAndStatusIn(
            client.getId(), ServiceRequestStatus.OPEN_STATUSES, PageRequest.of(0, 10));
    Page<ServiceRequestModel> firstOfTwo =
        requests.findPageByClientIdAndStatusIn(client.getId(), ANY_STATUS, PageRequest.of(0, 2));

    assertThat(all.getContent())
        .extracting(request -> request.getToken())
        .containsExactly(declined.getToken(), open.getToken(), expired.getToken());
    assertThat(openOnly.getContent())
        .extracting(request -> request.getToken())
        .containsExactly(open.getToken());
    assertThat(firstOfTwo.getTotalElements()).isEqualTo(3);
    assertThat(firstOfTwo.getContent()).hasSize(2);
  }

  @Test
  void pagesTheRequestsOfADriverFilteredByStatus() {
    ServiceRequestModel answered = saveRequest(link, ServiceRequestStatus.ANSWERED);
    ClientModel other = createClient("other@vanep.com", "20000000002");
    ServiceRequestModel converted =
        saveRequest(createLink(other, driver), ServiceRequestStatus.CONVERTED);
    saveRequest(
        createLink(client, createDriver("second@vanep.com", "52998224725")),
        ServiceRequestStatus.OPEN);

    Page<ServiceRequestModel> all =
        requests.findPageByDriverIdAndStatusIn(driver.getId(), ANY_STATUS, PageRequest.of(0, 10));
    Page<ServiceRequestModel> openOnly =
        requests.findPageByDriverIdAndStatusIn(
            driver.getId(), ServiceRequestStatus.OPEN_STATUSES, PageRequest.of(0, 10));

    assertThat(all.getContent())
        .extracting(request -> request.getToken())
        .containsExactly(converted.getToken(), answered.getToken());
    assertThat(openOnly.getContent())
        .extracting(request -> request.getToken())
        .containsExactly(answered.getToken());
  }

  @Test
  void loadsTheItemsAndDaysOfAPageInOneCall() {
    ServiceRequestModel first = saveRequest(link, ServiceRequestStatus.EXPIRED);
    ServiceRequestModel second =
        requests.save(
            newRequest(
                link,
                ServiceRequestStatus.OPEN,
                newItem(lucas, mondayRoundTrip()),
                newItem(ana, mondayRoundTrip())));

    List<ServiceRequestModel> found =
        requests.findWithItemsByIdIn(List.of(first.getId(), second.getId()));

    assertThat(found)
        .extracting(request -> request.getToken(), request -> request.getItems().size())
        .containsExactlyInAnyOrder(tuple(first.getToken(), 1), tuple(second.getToken(), 2));
    assertThat(found)
        .flatExtracting(request -> request.getItems())
        .allSatisfy(item -> assertThat(item.getDays()).hasSize(1));
  }

  @Test
  void countsTheOpenRequestsOfAClient() {
    saveRequest(link, ServiceRequestStatus.OPEN);
    saveRequest(link, ServiceRequestStatus.EXPIRED);
    saveRequest(
        createLink(client, createDriver("second@vanep.com", "52998224725")),
        ServiceRequestStatus.ANSWERED);
    saveRequest(
        createLink(createClient("other@vanep.com", "20000000002"), driver),
        ServiceRequestStatus.OPEN);

    assertThat(
            requests.countByClientIdAndStatusIn(client.getId(), ServiceRequestStatus.OPEN_STATUSES))
        .isEqualTo(2);
  }

  @Test
  void findsTheOpenRequestOfAPair() {
    saveRequest(link, ServiceRequestStatus.EXPIRED);
    ServiceRequestModel answered = saveRequest(link, ServiceRequestStatus.ANSWERED);
    ClientDriverModel closedPair =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    saveRequest(closedPair, ServiceRequestStatus.CONVERTED);

    assertThat(
            requests.findByClientDriverIdAndStatusIn(
                link.getId(), ServiceRequestStatus.OPEN_STATUSES))
        .get()
        .extracting(request -> request.getToken())
        .isEqualTo(answered.getToken());
    assertThat(
            requests.findByClientDriverIdAndStatusIn(
                closedPair.getId(), ServiceRequestStatus.OPEN_STATUSES))
        .isEmpty();
  }

  @Test
  void findsOnlyTheOpenRequestsWhoseDeadlineHasPassed() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    ServiceRequestModel overdue =
        saveRequest(link, ServiceRequestStatus.OPEN, now.minusSeconds(60));
    ServiceRequestModel dueNow =
        saveRequest(
            createLink(client, createDriver("second@vanep.com", "52998224725")),
            ServiceRequestStatus.OPEN,
            now);
    ClientModel other = createClient("other@vanep.com", "20000000002");
    saveRequest(createLink(other, driver), ServiceRequestStatus.OPEN, now.plusSeconds(60));
    saveRequest(
        createLink(other, createDriver("third@vanep.com", "39053344705")),
        ServiceRequestStatus.ANSWERED,
        now.minusSeconds(60));

    assertThat(requests.findOverdueOpenIds(now, Limit.unlimited()))
        .containsExactly(overdue.getId(), dueNow.getId());
  }

  @Test
  void findsTheOldestOverdueRequestsFirstUpToTheLimit() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    saveRequest(link, ServiceRequestStatus.OPEN, now.minusSeconds(60));
    ServiceRequestModel oldest =
        saveRequest(
            createLink(client, createDriver("second@vanep.com", "52998224725")),
            ServiceRequestStatus.OPEN,
            now.minusSeconds(180));
    ServiceRequestModel second =
        saveRequest(
            createLink(client, createDriver("third@vanep.com", "39053344705")),
            ServiceRequestStatus.OPEN,
            now.minusSeconds(120));

    assertThat(requests.findOverdueOpenIds(now, Limit.of(2)))
        .containsExactly(oldest.getId(), second.getId());
  }

  @Test
  void expiresARequestOnlyWhileItIsOpenAndOverdue() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    ServiceRequestModel overdue =
        saveRequest(link, ServiceRequestStatus.OPEN, now.minusSeconds(60));
    ServiceRequestModel onTime =
        saveRequest(
            createLink(client, createDriver("second@vanep.com", "52998224725")),
            ServiceRequestStatus.OPEN,
            now.plusSeconds(60));
    ServiceRequestModel answered =
        saveRequest(
            createLink(client, createDriver("third@vanep.com", "39053344705")),
            ServiceRequestStatus.ANSWERED,
            now.minusSeconds(60));

    assertThat(expire(overdue, now)).isEqualTo(1);
    assertThat(expire(overdue, now)).isZero();
    assertThat(expire(onTime, now)).isZero();
    assertThat(expire(answered, now)).isZero();
    assertThat(statusOf(overdue)).isEqualTo(ServiceRequestStatus.EXPIRED);
    assertThat(statusOf(onTime)).isEqualTo(ServiceRequestStatus.OPEN);
    assertThat(statusOf(answered)).isEqualTo(ServiceRequestStatus.ANSWERED);
  }

  @Test
  void generatesAnOpaqueTokenOnPersist() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);

    assertThat(saved.getToken()).isNotBlank().doesNotContain("-");
  }

  @Test
  void changesTheStatusOnlyWhenTheRowIsInAnExpectedStatus() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);
    Instant createdUpdatedAt = saved.getUpdatedAt();

    Integer fromAnswered =
        transactions.execute(
            status ->
                requests.updateStatus(
                    saved.getId(),
                    Set.of(ServiceRequestStatus.ANSWERED),
                    ServiceRequestStatus.CONVERTED));
    assertThat(fromAnswered).isZero();
    assertThat(statusOf(saved)).isEqualTo(ServiceRequestStatus.OPEN);

    Integer fromOpen =
        transactions.execute(
            status ->
                requests.updateStatus(
                    saved.getId(),
                    ServiceRequestStatus.OPEN_STATUSES,
                    ServiceRequestStatus.WITHDRAWN));
    ServiceRequestModel reread =
        requests.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();

    assertThat(fromOpen).isEqualTo(1);
    assertThat(reread.getStatus()).isEqualTo(ServiceRequestStatus.WITHDRAWN);
    assertThat(reread.getUpdatedAt()).isAfterOrEqualTo(createdUpdatedAt);
  }

  @Test
  void aRereadInTheSameTransactionSeesTheStatusTheConditionalWriteLeft() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);

    ServiceRequestModel reread =
        transactions.execute(
            status -> {
              requests.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();
              requests.updateStatus(
                  saved.getId(),
                  ServiceRequestStatus.OPEN_STATUSES,
                  ServiceRequestStatus.WITHDRAWN);
              return requests
                  .findByTokenAndClientId(saved.getToken(), client.getId())
                  .orElseThrow();
            });

    assertThat(reread.getStatus()).isEqualTo(ServiceRequestStatus.WITHDRAWN);
  }

  @Test
  void declineWritesTheReasonWithTheStatusOnlyFromAnExpectedStatus() {
    ServiceRequestModel saved = saveRequest(link, ServiceRequestStatus.OPEN);

    Integer fromAnswered =
        transactions.execute(
            status ->
                requests.updateStatusWithDeclineReason(
                    saved.getId(),
                    Set.of(ServiceRequestStatus.ANSWERED),
                    ServiceRequestStatus.DECLINED,
                    "Sem vaga"));
    ServiceRequestModel reread =
        transactions.execute(
            status -> {
              requests.updateStatusWithDeclineReason(
                  saved.getId(),
                  Set.of(ServiceRequestStatus.OPEN),
                  ServiceRequestStatus.DECLINED,
                  "Sem vaga");
              return requests
                  .findByTokenAndClientId(saved.getToken(), client.getId())
                  .orElseThrow();
            });

    assertThat(fromAnswered).isZero();
    assertThat(reread.getStatus()).isEqualTo(ServiceRequestStatus.DECLINED);
    assertThat(reread.getDeclineReason()).isEqualTo("Sem vaga");
  }

  @Test
  void answersOnlyARequestStillOpenInTimeOrAlreadyAnswered() {
    ServiceRequestModel inTime = saveRequest(link, ServiceRequestStatus.OPEN);
    ServiceRequestModel answered =
        saveRequest(
            createLink(client, createDriver("answered@vanep.com", "39053344705")),
            ServiceRequestStatus.ANSWERED);
    ServiceRequestModel overdue =
        saveRequest(
            createLink(client, createDriver("overdue@vanep.com", "86288366757")),
            ServiceRequestStatus.OPEN,
            Instant.now().minusSeconds(60));
    ServiceRequestModel converted =
        saveRequest(
            createLink(client, createDriver("converted@vanep.com", "12345678909")),
            ServiceRequestStatus.CONVERTED);
    Instant now = Instant.now();

    assertThat(answer(inTime, now)).isEqualTo(1);
    assertThat(answer(answered, now)).isEqualTo(1);
    assertThat(answer(overdue, now)).isZero();
    assertThat(answer(converted, now)).isZero();
    assertThat(statusOf(inTime)).isEqualTo(ServiceRequestStatus.ANSWERED);
    assertThat(statusOf(overdue)).isEqualTo(ServiceRequestStatus.OPEN);
    assertThat(statusOf(converted)).isEqualTo(ServiceRequestStatus.CONVERTED);
  }

  @Test
  void reopensOnlyAnAnsweredRequestWithTheNewDeadline() {
    ServiceRequestModel answered = saveRequest(link, ServiceRequestStatus.ANSWERED);
    ServiceRequestModel declined =
        saveRequest(
            createLink(client, createDriver("declined@vanep.com", "39053344705")),
            ServiceRequestStatus.DECLINED);
    Instant deadline = Instant.now().plus(Duration.ofHours(72)).truncatedTo(ChronoUnit.SECONDS);

    Integer fromAnswered =
        transactions.execute(status -> requests.reopen(answered.getId(), deadline));
    Integer fromDeclined =
        transactions.execute(status -> requests.reopen(declined.getId(), deadline));
    ServiceRequestModel reread =
        requests.findByTokenAndClientId(answered.getToken(), client.getId()).orElseThrow();

    assertThat(fromAnswered).isEqualTo(1);
    assertThat(fromDeclined).isZero();
    assertThat(reread.getStatus()).isEqualTo(ServiceRequestStatus.OPEN);
    assertThat(reread.getExpiresAt()).isEqualTo(deadline);
    assertThat(statusOf(declined)).isEqualTo(ServiceRequestStatus.DECLINED);
  }

  private Integer expire(ServiceRequestModel request, Instant now) {
    return transactions.execute(status -> requests.expire(request.getId(), now));
  }

  private Integer answer(ServiceRequestModel request, Instant now) {
    return transactions.execute(status -> requests.answer(request.getId(), now));
  }

  private ServiceRequestStatus statusOf(ServiceRequestModel request) {
    return requests
        .findByTokenAndClientId(request.getToken(), client.getId())
        .orElseThrow()
        .getStatus();
  }

  private ServiceRequestItemModel itemOf(ServiceRequestModel request, DependentModel dependent) {
    return request.getItems().stream()
        .filter(item -> item.getDependentId().equals(dependent.getId()))
        .findFirst()
        .orElseThrow();
  }

  private ServiceRequestModel saveRequest(ClientDriverModel owner, ServiceRequestStatus status) {
    return saveRequest(owner, status, Instant.now().plus(Duration.ofHours(72)));
  }

  private ServiceRequestModel saveRequest(
      ClientDriverModel owner, ServiceRequestStatus status, Instant expiresAt) {
    ServiceRequestModel request = newRequest(owner, status, newItem(lucas, mondayRoundTrip()));
    request.setExpiresAt(expiresAt);
    return requests.save(request);
  }

  private ServiceRequestModel newRequest(
      ClientDriverModel owner, ServiceRequestStatus status, ServiceRequestItemModel... items) {
    ServiceRequestModel request = new ServiceRequestModel();
    request.setClientDriver(owner);
    request.setStatus(status);
    request.setDesiredStartDate(DESIRED_START);
    request.setNotes("Portão azul");
    request.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
    for (ServiceRequestItemModel item : items) {
      request.addItem(item);
    }
    return request;
  }

  private ServiceRequestItemModel newItem(
      DependentModel dependent, ServiceRequestDayModel... days) {
    return newItemIn("Cambuí", dependent, days);
  }

  private ServiceRequestItemModel newItemIn(
      String pickupNeighborhood, DependentModel dependent, ServiceRequestDayModel... days) {
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(dependent.getId());
    item.setSchoolId(school.getId());
    item.setPickupCityId(city.getId());
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent(
                dependent.getName(), dependent.getBirthDate()),
            new ServiceRequestItemModel.Snapshot.School(school.getName()),
            new ServiceRequestItemModel.Snapshot.Pickup(
                city.getName(), city.getState().getUf(), pickupNeighborhood)));
    for (ServiceRequestDayModel day : days) {
      item.addDay(day);
    }
    return item;
  }

  private ServiceRequestDayModel mondayRoundTrip() {
    return newDay(DayOfWeek.MONDAY, SchoolShift.MORNING, true, true);
  }

  private ServiceRequestDayModel newDay(
      DayOfWeek weekday, SchoolShift schoolShift, boolean needsOutbound, boolean needsReturn) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(weekday);
    day.setSchoolShift(schoolShift);
    day.setNeedsOutbound(needsOutbound);
    day.setNeedsReturn(needsReturn);
    return day;
  }

  private DependentModel createDependent(ClientModel owner, String name, LocalDate birthDate) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(owner.getId());
    dependent.setName(name);
    dependent.setBirthDate(birthDate);
    return dependents.save(dependent);
  }

  private ClientDriverModel createLink(ClientModel owner, DriverModel linkedDriver) {
    ClientDriverModel model = new ClientDriverModel();
    model.setClient(owner);
    model.setDriver(linkedDriver);
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
