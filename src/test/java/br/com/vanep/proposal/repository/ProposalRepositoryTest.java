package br.com.vanep.proposal.repository;

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
import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.model.ProposalAdjustmentModel;
import br.com.vanep.proposal.model.ProposalItemModel;
import br.com.vanep.proposal.model.ProposalModel;
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
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProposalRepositoryTest {

  private static final LocalDate STARTS_ON = LocalDate.of(2027, 2, 1);

  @Autowired private ProposalRepository proposals;
  @Autowired private ProposalAdjustmentRepository adjustments;
  @Autowired private ServiceRequestRepository requests;
  @Autowired private VehicleRepository vehicles;
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

  private CityModel city;
  private SchoolModel school;
  private ClientModel client;
  private DriverModel driver;
  private ClientDriverModel link;
  private VehicleModel vehicle;
  private DependentModel lucas;
  private DependentModel ana;

  @BeforeEach
  void setUp() {
    city = createCity();
    school = createSchool();
    client = createClient("client@vanep.com", "10000000001");
    driver = createDriver("driver@vanep.com", "11144477735");
    link = createLink(client, driver);
    vehicle = createVehicle(driver, "ABC1D23");
    lucas = createDependent(client, "Lucas Souza");
    ana = createDependent(client, "Ana Souza");
  }

  @Test
  void rereadsAProposalOutsideTheTransactionWithItsItemsSchedulesAndAdjustments() {
    ServiceRequestModel request = saveRequest(link, ServiceRequestStatus.ANSWERED);
    ProposalModel saved =
        proposals.save(
            newProposal(
                request,
                1,
                ProposalStatus.SENT,
                newItem(lucas, "350.00", RouteLeg.OUTBOUND, RouteLeg.RETURN),
                newItem(ana, "300.00", RouteLeg.OUTBOUND)));
    ProposalAdjustmentModel price =
        adjustments.save(newAdjustment(saved, ProposalAdjustmentField.PRICE, "320,00", null));
    ProposalAdjustmentModel schedule =
        adjustments.save(
            newAdjustment(saved, ProposalAdjustmentField.SCHEDULE, null, "Só ida às quartas"));

    ProposalModel found =
        proposals.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();

    assertThat(found.getVehicleId()).isEqualTo(vehicle.getId());
    assertThat(found.getServiceRequest().getId()).isEqualTo(request.getId());
    assertThat(found.getAdjustmentPolicy()).isEqualTo(ProposalAdjustmentPolicy.NONE);
    assertThat(found.getCancellationPolicy()).isEqualTo(ProposalCancellationPolicy.NOTICE_30_DAYS);
    assertThat(found.getItems())
        .extracting(
            item -> item.getDependentId(),
            item -> item.getMonthlyAmount().toPlainString(),
            item -> item.getSchedule().getSlots().size())
        .containsExactlyInAnyOrder(
            tuple(lucas.getId(), "350.00", 2), tuple(ana.getId(), "300.00", 1));
    assertThat(adjustments.findByProposalIdInOrderByCreatedAtAscIdAsc(List.of(saved.getId())))
        .extracting(adjustment -> adjustment.getId(), adjustment -> adjustment.getField())
        .containsExactly(
            tuple(price.getId(), ProposalAdjustmentField.PRICE),
            tuple(schedule.getId(), ProposalAdjustmentField.SCHEDULE));
  }

  @Test
  void theVehicleSnapshotOutlivesEditsRemovalsAndRestoresOfTheVehicle() {
    ProposalModel saved =
        proposals.save(
            newProposal(
                saveRequest(link, ServiceRequestStatus.CONVERTED),
                1,
                ProposalStatus.WITHDRAWN,
                newItem(lucas, "350.00", RouteLeg.OUTBOUND)));

    VehicleModel edited = vehicles.findById(vehicle.getId()).orElseThrow();
    edited.setPlate("XYZ9W87");
    edited.setCapacity(8);
    vehicles.save(edited);
    vehicles.delete(vehicles.findById(vehicle.getId()).orElseThrow());
    transactions.executeWithoutResult(status -> vehicles.restoreByToken(vehicle.getToken()));
    vehicles.delete(vehicles.findById(vehicle.getId()).orElseThrow());

    assertThat(proposals.findByTokenAndDriverId(saved.getToken(), driver.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getVehicleId()).isEqualTo(vehicle.getId());
              assertThat(found.getSnapshot().vehicle().plate()).isEqualTo("ABC1D23");
              assertThat(found.getSnapshot().vehicle().capacity()).isEqualTo(15);
            });
  }

  @Test
  void writesOnlyTheStatusFieldsAfterCreation() {
    ProposalModel saved = saveSentProposal(link);
    ProposalModel.Snapshot offered = saved.getSnapshot();

    ProposalModel changed = proposals.findById(saved.getId()).orElseThrow();
    changed.setTotalAmount(new BigDecimal("1.00"));
    changed.setInstallments(1);
    changed.setMessage("Outra mensagem");
    changed.setSnapshot(
        new ProposalModel.Snapshot(
            ProposalModel.Snapshot.CURRENT_VERSION,
            new ProposalModel.Snapshot.Vehicle("XYZ9W87", "Fiat", "Ducato", 2010, "Black", 8)));
    changed.setStatus(ProposalStatus.DECLINED);
    changed.setDeclineReason("Muito caro");
    proposals.save(changed);

    ProposalModel reread = proposals.findById(saved.getId()).orElseThrow();
    assertThat(reread.getTotalAmount()).isEqualByComparingTo("4200.00");
    assertThat(reread.getInstallments()).isEqualTo(12);
    assertThat(reread.getMessage()).isEqualTo("Saio da garagem às 6h");
    assertThat(reread.getSnapshot()).isEqualTo(offered);
    assertThat(reread.getStatus()).isEqualTo(ProposalStatus.DECLINED);
    assertThat(reread.getDeclineReason()).isEqualTo("Muito caro");
  }

  @Test
  void findsByTokenOnlyWithinTheClient() {
    ProposalModel saved = saveSentProposal(link);
    ClientModel other = createClient("other@vanep.com", "20000000002");

    assertThat(proposals.findByTokenAndClientId(saved.getToken(), client.getId())).isPresent();
    assertThat(proposals.findByTokenAndClientId(saved.getToken(), other.getId())).isEmpty();
    assertThat(proposals.findByTokenAndClientId("missing", client.getId())).isEmpty();
  }

  @Test
  void findsByTokenOnlyWithinTheDriver() {
    ProposalModel saved = saveSentProposal(link);
    DriverModel other = createDriver("other-driver@vanep.com", "52998224725");

    assertThat(proposals.findByTokenAndDriverId(saved.getToken(), driver.getId())).isPresent();
    assertThat(proposals.findByTokenAndDriverId(saved.getToken(), other.getId())).isEmpty();
  }

  @Test
  void returnsTheVersionChainOfARequestOldestFirst() {
    ServiceRequestModel request = saveRequest(link, ServiceRequestStatus.ANSWERED);
    ProposalModel first =
        proposals.save(
            newProposal(
                request,
                1,
                ProposalStatus.SUPERSEDED,
                newItem(lucas, "350.00", RouteLeg.OUTBOUND)));
    ProposalModel second =
        newProposal(request, 2, ProposalStatus.SENT, newItem(lucas, "330.00", RouteLeg.OUTBOUND));
    second.setParentProposalId(first.getId());
    proposals.save(second);
    ClientDriverModel otherLink =
        createLink(createClient("other@vanep.com", "20000000002"), driver);
    saveSentProposal(otherLink);

    List<ProposalModel> chain = proposals.findChainByServiceRequestId(request.getId());

    assertThat(chain)
        .extracting(
            proposal -> proposal.getVersion(),
            proposal -> proposal.getParentProposalId(),
            proposal -> proposal.getItems().iterator().next().getMonthlyAmount().toPlainString())
        .containsExactly(tuple(1, null, "350.00"), tuple(2, first.getId(), "330.00"));
  }

  @Test
  void findsTheCurrentProposalOfSeveralRequestsInOneCall() {
    ServiceRequestModel answered = saveRequest(link, ServiceRequestStatus.ANSWERED);
    proposals.save(
        newProposal(
            answered, 1, ProposalStatus.SUPERSEDED, newItem(lucas, "350.00", RouteLeg.OUTBOUND)));
    ProposalModel sent =
        proposals.save(
            newProposal(
                answered, 2, ProposalStatus.SENT, newItem(lucas, "330.00", RouteLeg.OUTBOUND)));
    adjustments.save(newAdjustment(sent, ProposalAdjustmentField.PRICE, "320,00", null));
    ClientDriverModel convertedPair =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    ServiceRequestModel converted = saveRequest(convertedPair, ServiceRequestStatus.CONVERTED);
    ProposalModel accepted =
        proposals.save(
            newProposal(
                converted,
                1,
                ProposalStatus.ACCEPTED,
                newItem(lucas, "350.00", RouteLeg.OUTBOUND)));
    ClientDriverModel declinedPair =
        createLink(client, createDriver("third@vanep.com", "39053344705"));
    ServiceRequestModel declined = saveRequest(declinedPair, ServiceRequestStatus.WITHDRAWN);
    proposals.save(
        newProposal(
            declined, 1, ProposalStatus.DECLINED, newItem(lucas, "350.00", RouteLeg.OUTBOUND)));

    List<ProposalRepository.CurrentProposalRow> rows =
        proposals.findCurrentByServiceRequestIdIn(
            List.of(answered.getId(), converted.getId(), declined.getId()));

    assertThat(rows)
        .extracting(
            row -> row.getServiceRequestId(),
            row -> row.getToken(),
            row -> row.getVersion(),
            row -> row.getStatus(),
            row -> row.getTotalAmount().toPlainString(),
            row -> row.getInstallments(),
            row -> row.getHasAdjustments())
        .containsExactlyInAnyOrder(
            tuple(answered.getId(), sent.getToken(), 2, ProposalStatus.SENT, "4200.00", 12, true),
            tuple(
                converted.getId(),
                accepted.getToken(),
                1,
                ProposalStatus.ACCEPTED,
                "4200.00",
                12,
                false));
    assertThat(rows)
        .allMatch(row -> row.getValidUntil() != null && row.getStartsOn().equals(STARTS_ON));
  }

  @Test
  void findsTheSentProposalOfAPair() {
    ServiceRequestModel request = saveRequest(link, ServiceRequestStatus.ANSWERED);
    proposals.save(
        newProposal(
            request, 1, ProposalStatus.SUPERSEDED, newItem(lucas, "350.00", RouteLeg.OUTBOUND)));
    ProposalModel sent =
        proposals.save(
            newProposal(
                request, 2, ProposalStatus.SENT, newItem(lucas, "330.00", RouteLeg.OUTBOUND)));
    ClientDriverModel closedPair =
        createLink(client, createDriver("second@vanep.com", "52998224725"));
    proposals.save(
        newProposal(
            saveRequest(closedPair, ServiceRequestStatus.CONVERTED),
            1,
            ProposalStatus.ACCEPTED,
            newItem(lucas, "350.00", RouteLeg.OUTBOUND)));

    assertThat(proposals.findByClientDriverIdAndStatus(link.getId(), ProposalStatus.SENT))
        .get()
        .extracting(proposal -> proposal.getToken())
        .isEqualTo(sent.getToken());
    assertThat(proposals.findByClientDriverIdAndStatus(closedPair.getId(), ProposalStatus.SENT))
        .isEmpty();
  }

  @Test
  void findsOnlyTheSentProposalsWhoseValidityHasPassed() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    ProposalModel overdue = saveSentProposal(link, now.minusSeconds(60));
    ProposalModel dueNow =
        saveSentProposal(createLink(client, createDriver("second@vanep.com", "52998224725")), now);
    ClientModel other = createClient("other@vanep.com", "20000000002");
    saveSentProposal(createLink(other, driver), now.plusSeconds(60));
    ProposalModel declined =
        newProposal(
            saveRequest(
                createLink(other, createDriver("third@vanep.com", "39053344705")),
                ServiceRequestStatus.WITHDRAWN),
            1,
            ProposalStatus.DECLINED,
            newItem(lucas, "350.00", RouteLeg.OUTBOUND));
    declined.setValidUntil(now.minusSeconds(60));
    proposals.save(declined);

    assertThat(proposals.findOverdueSentIds(now)).containsExactly(overdue.getId(), dueNow.getId());
  }

  @Test
  void generatesAnOpaqueTokenOnPersist() {
    assertThat(saveSentProposal(link).getToken()).isNotBlank().doesNotContain("-");
  }

  @Test
  void changesTheStatusOnlyWhenTheRowIsInTheExpectedStatus() {
    ProposalModel saved = saveSentProposal(link);

    Integer fromAccepted =
        transactions.execute(
            status ->
                proposals.updateStatus(
                    saved.getId(), ProposalStatus.ACCEPTED, ProposalStatus.SUPERSEDED));
    assertThat(fromAccepted).isZero();
    assertThat(statusOf(saved)).isEqualTo(ProposalStatus.SENT);

    Integer fromSent =
        transactions.execute(
            status ->
                proposals.updateStatus(
                    saved.getId(), ProposalStatus.SENT, ProposalStatus.WITHDRAWN));

    assertThat(fromSent).isEqualTo(1);
    assertThat(statusOf(saved)).isEqualTo(ProposalStatus.WITHDRAWN);
  }

  @Test
  void findsTheAdjustmentsOfSeveralProposalsInOneCall() {
    ProposalModel first = saveSentProposal(link);
    ProposalModel second =
        saveSentProposal(createLink(client, createDriver("second@vanep.com", "52998224725")));
    adjustments.save(newAdjustment(first, ProposalAdjustmentField.PERIOD, "fevereiro", null));
    adjustments.save(newAdjustment(second, ProposalAdjustmentField.OTHER, null, "Tem vaga?"));

    assertThat(
            adjustments.findByProposalIdInOrderByCreatedAtAscIdAsc(
                List.of(first.getId(), second.getId())))
        .extracting(adjustment -> adjustment.getProposalId())
        .containsExactly(first.getId(), second.getId());
  }

  @Test
  void findsTheLatestVersionOfARequest() {
    ServiceRequestModel request = saveRequest(link, ServiceRequestStatus.ANSWERED);
    proposals.save(
        newProposal(
            request, 1, ProposalStatus.SUPERSEDED, newItem(lucas, "350.00", RouteLeg.OUTBOUND)));
    ProposalModel second =
        proposals.save(
            newProposal(
                request, 2, ProposalStatus.WITHDRAWN, newItem(lucas, "340.00", RouteLeg.OUTBOUND)));

    assertThat(proposals.findTopByServiceRequestIdOrderByVersionDesc(request.getId()))
        .get()
        .extracting(proposal -> proposal.getId())
        .isEqualTo(second.getId());
    assertThat(
            proposals.findTopByServiceRequestIdOrderByVersionDesc(
                saveRequest(
                        createLink(client, createDriver("new@vanep.com", "52998224725")),
                        ServiceRequestStatus.OPEN)
                    .getId()))
        .isEmpty();
  }

  @Test
  void acceptsOnlyASentProposalStillValidAndStampsTheAcceptance() {
    ProposalModel valid = saveSentProposal(link);
    ProposalModel overdue =
        saveSentProposal(
            createLink(client, createDriver("overdue@vanep.com", "52998224725")),
            Instant.now().minusSeconds(60));
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    Integer accepted = transactions.execute(status -> proposals.accept(valid.getId(), now));
    Integer late = transactions.execute(status -> proposals.accept(overdue.getId(), now));
    Integer again = transactions.execute(status -> proposals.accept(valid.getId(), now));
    ProposalModel reread =
        proposals.findByTokenAndClientId(valid.getToken(), client.getId()).orElseThrow();

    assertThat(accepted).isEqualTo(1);
    assertThat(late).isZero();
    assertThat(again).isZero();
    assertThat(reread.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    assertThat(reread.getAcceptedAt()).isEqualTo(now);
    assertThat(statusOf(overdue)).isEqualTo(ProposalStatus.SENT);
  }

  @Test
  void declinesOnlyASentProposalWithTheReason() {
    ProposalModel saved = saveSentProposal(link);

    Integer declined =
        transactions.execute(status -> proposals.decline(saved.getId(), "Caro demais"));
    Integer again = transactions.execute(status -> proposals.decline(saved.getId(), "De novo"));
    ProposalModel reread =
        proposals.findByTokenAndClientId(saved.getToken(), client.getId()).orElseThrow();

    assertThat(declined).isEqualTo(1);
    assertThat(again).isZero();
    assertThat(reread.getStatus()).isEqualTo(ProposalStatus.DECLINED);
    assertThat(reread.getDeclineReason()).isEqualTo("Caro demais");
  }

  @Test
  void touchesOnlyASentProposal() {
    ProposalModel saved = saveSentProposal(link);

    Integer whileSent = transactions.execute(status -> proposals.touchSent(saved.getId()));
    transactions.execute(
        status ->
            proposals.updateStatus(saved.getId(), ProposalStatus.SENT, ProposalStatus.EXPIRED));
    Integer afterExpired = transactions.execute(status -> proposals.touchSent(saved.getId()));

    assertThat(whileSent).isEqualTo(1);
    assertThat(afterExpired).isZero();
    assertThat(statusOf(saved)).isEqualTo(ProposalStatus.EXPIRED);
  }

  private ProposalStatus statusOf(ProposalModel proposal) {
    return proposals
        .findByTokenAndClientId(proposal.getToken(), client.getId())
        .orElseThrow()
        .getStatus();
  }

  private ProposalModel saveSentProposal(ClientDriverModel owner) {
    return saveSentProposal(owner, Instant.now().plus(Duration.ofDays(7)));
  }

  private ProposalModel saveSentProposal(ClientDriverModel owner, Instant validUntil) {
    ProposalModel proposal =
        newProposal(
            saveRequest(owner, ServiceRequestStatus.ANSWERED),
            1,
            ProposalStatus.SENT,
            newItem(lucas, "350.00", RouteLeg.OUTBOUND));
    proposal.setValidUntil(validUntil);
    return proposals.save(proposal);
  }

  private ProposalModel newProposal(
      ServiceRequestModel request,
      int version,
      ProposalStatus status,
      ProposalItemModel... proposalItems) {
    ProposalModel proposal = new ProposalModel();
    proposal.setClientDriver(request.getClientDriver());
    proposal.setServiceRequest(request);
    proposal.setVersion(version);
    proposal.setStatus(status);
    proposal.setValidUntil(Instant.now().plus(Duration.ofDays(7)));
    proposal.setStartsOn(STARTS_ON);
    proposal.setEndsOn(STARTS_ON.plusMonths(10));
    proposal.setTotalAmount(new BigDecimal("4200.00"));
    proposal.setInstallments(12);
    proposal.setDueDay(5);
    proposal.setEnrollmentFee(new BigDecimal("100.00"));
    proposal.setAdjustmentPolicy(ProposalAdjustmentPolicy.NONE);
    proposal.setCancellationPolicy(ProposalCancellationPolicy.NOTICE_30_DAYS);
    proposal.setVehicleId(vehicle.getId());
    proposal.setSnapshot(
        new ProposalModel.Snapshot(
            ProposalModel.Snapshot.CURRENT_VERSION,
            new ProposalModel.Snapshot.Vehicle(
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getColor(),
                vehicle.getCapacity())));
    proposal.setMessage("Saio da garagem às 6h");
    if (status == ProposalStatus.ACCEPTED) {
      proposal.setAcceptedAt(Instant.now());
    }
    for (ProposalItemModel item : proposalItems) {
      proposal.addItem(item);
    }
    return proposal;
  }

  private ProposalItemModel newItem(DependentModel dependent, String monthly, RouteLeg... legs) {
    ProposalItemModel item = new ProposalItemModel();
    item.setDependentId(dependent.getId());
    item.setMonthlyAmount(new BigDecimal(monthly));
    ScheduleModel schedule = new ScheduleModel();
    for (RouteLeg leg : legs) {
      ScheduleSlotModel slot = new ScheduleSlotModel();
      slot.setWeekday(DayOfWeek.MONDAY);
      slot.setLeg(leg);
      slot.setShift(leg == RouteLeg.OUTBOUND ? OperationShift.MORNING : OperationShift.AFTERNOON);
      slot.setWindowStart(leg == RouteLeg.OUTBOUND ? LocalTime.of(6, 40) : LocalTime.of(12, 10));
      schedule.addSlot(slot);
    }
    item.setSchedule(schedule);
    return item;
  }

  private ProposalAdjustmentModel newAdjustment(
      ProposalModel proposal, ProposalAdjustmentField field, String suggestedValue, String note) {
    ProposalAdjustmentModel adjustment = new ProposalAdjustmentModel();
    adjustment.setProposalId(proposal.getId());
    adjustment.setField(field);
    adjustment.setSuggestedValue(suggestedValue);
    adjustment.setNote(note);
    return adjustment;
  }

  private ServiceRequestModel saveRequest(ClientDriverModel owner, ServiceRequestStatus status) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(DayOfWeek.MONDAY);
    day.setSchoolShift(SchoolShift.MORNING);
    day.setNeedsOutbound(true);
    day.setNeedsReturn(true);
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(lucas.getId());
    item.setSchoolId(school.getId());
    item.setPickupCityId(city.getId());
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent(
                lucas.getName(), LocalDate.of(2016, 4, 2)),
            new ServiceRequestItemModel.Snapshot.School(school.getName()),
            new ServiceRequestItemModel.Snapshot.Pickup(city.getName(), "SP", null)));
    item.addDay(day);
    ServiceRequestModel request = new ServiceRequestModel();
    request.setClientDriver(owner);
    request.setStatus(status);
    request.setDesiredStartDate(STARTS_ON);
    request.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
    request.addItem(item);
    return requests.save(request);
  }

  private VehicleModel createVehicle(DriverModel owner, String plate) {
    VehicleModel model = new VehicleModel();
    model.setDriver(owner);
    model.setPlate(plate);
    model.setBrand("Ford");
    model.setModel("Transit");
    model.setManufactureYear(2022);
    model.setColor("White");
    model.setCapacity(15);
    return vehicles.save(model);
  }

  private DependentModel createDependent(ClientModel owner, String name) {
    DependentModel dependent = new DependentModel();
    dependent.setClientId(owner.getId());
    dependent.setName(name);
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
