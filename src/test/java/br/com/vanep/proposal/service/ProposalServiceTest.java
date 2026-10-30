package br.com.vanep.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.contract.service.ContractTermsPolicy;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.dto.ProposalAdjustmentRequestDTO;
import br.com.vanep.proposal.dto.ProposalCreateRequestDTO;
import br.com.vanep.proposal.dto.ProposalDeclineRequestDTO;
import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.proposal.dto.ProposalResponseDTO;
import br.com.vanep.proposal.enums.ProposalAdjustmentField;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.proposal.mapper.ProposalMapper;
import br.com.vanep.proposal.model.ProposalAdjustmentModel;
import br.com.vanep.proposal.model.ProposalItemModel;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.proposal.repository.ProposalAdjustmentRepository;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.schedule.service.ScheduleService;
import br.com.vanep.schedule.service.ScheduleSlotPolicy;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalServiceTest {

  private static final String ANA_UID = "ana-uid";
  private static final String CARLOS_UID = "carlos-uid";
  private static final LocalDate STARTS_ON = LocalDate.now().plusMonths(1).withDayOfMonth(1);

  @Mock private ProposalRepository proposals;
  @Mock private ProposalAdjustmentRepository adjustments;
  @Mock private ServiceRequestRepository requests;
  @Mock private UserService users;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private DependentRepository dependents;
  @Mock private DependentSchoolDayRepository schoolDays;
  @Mock private AddressRepository addresses;
  @Mock private VehicleRepository vehicles;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private TransactionTemplate transactions;
  @Mock private ApplicationEventPublisher events;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<ProposalModel> savedProposal;
  @Captor private ArgumentCaptor<ProposalAdjustmentModel> savedAdjustment;

  private ProposalService service;
  private ClientDriverModel link;
  private DriverModel carlos;
  private ServiceRequestModel request;
  private DependentModel lucas;
  private DependentModel marina;
  private AddressModel lucasHome;
  private List<DependentSchoolDayModel> grids;
  private VehicleModel van;
  private ProposalModel sent;

  @BeforeEach
  void setUp() {
    service =
        new ProposalService(
            proposals,
            adjustments,
            requests,
            users,
            clients,
            drivers,
            dependents,
            schoolDays,
            addresses,
            vehicles,
            new ScheduleService(scheduleRepository, new ScheduleSlotPolicy(), messages),
            new ContractTermsPolicy(),
            new ProposalAcceptancePolicy(),
            new ProposalMapper(new ScheduleMapper(), new ServiceRequestMapper()),
            transactions,
            events,
            messages);
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
    doAnswer(
            call -> {
              call.<Consumer<TransactionStatus>>getArgument(0).accept(null);
              return null;
            })
        .when(transactions)
        .executeWithoutResult(any());

    UserModel anaUser = user(1L, "Ana Souza", UserType.CLIENT);
    anaUser.setToken(ANA_UID);
    ClientModel ana = new ClientModel();
    ana.setId(10L);
    ana.setUser(anaUser);
    when(users.requireByTokenAndType(ANA_UID, UserType.CLIENT)).thenReturn(anaUser);
    when(clients.findByUserId(1L)).thenReturn(Optional.of(ana));

    UserModel carlosUser = user(2L, "Carlos Pereira", UserType.DRIVER);
    carlosUser.setToken(CARLOS_UID);
    carlos = new DriverModel();
    carlos.setId(20L);
    carlos.setUser(carlosUser);
    carlos.setApprovalStatus(DriverApprovalStatus.APPROVED);
    when(users.requireByTokenAndType(CARLOS_UID, UserType.DRIVER)).thenReturn(carlosUser);
    when(drivers.findByUserId(2L)).thenReturn(Optional.of(carlos));

    link = new ClientDriverModel();
    link.setId(40L);
    link.setToken("link");
    link.setClient(ana);
    link.setDriver(carlos);

    request = new ServiceRequestModel();
    request.setId(50L);
    request.setToken("request");
    request.setClientDriver(link);
    request.setStatus(ServiceRequestStatus.OPEN);
    request.setExpiresAt(Instant.now().plus(Duration.ofHours(72)));
    request.addItem(
        requestItem(101L, 21L, "Lucas Souza", "Cambuí", SchoolShift.MORNING, SchoolShift.FULLTIME));
    request.addItem(requestItem(102L, 22L, "Marina Souza", null, SchoolShift.MORNING));
    when(requests.findByTokenAndDriverId("request", 20L)).thenReturn(Optional.of(request));
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(request));
    when(requests.findWithItemsByIdIn(List.of(50L))).thenReturn(List.of(request));
    when(requests.answer(eq(50L), any())).thenReturn(1);

    lucas = dependent(21L, "Lucas Souza", 31L);
    marina = dependent(22L, "Marina Souza", 32L);
    when(dependents.findTokensByIdIn(any()))
        .thenReturn(List.of(new IdAndToken(21L, "lucas"), new IdAndToken(22L, "marina")));
    when(dependents.findAllById(List.of(21L, 22L))).thenReturn(List.of(lucas, marina));
    when(dependents.lockByIdIn(List.of(21L, 22L))).thenReturn(List.of(lucas, marina));
    lucasHome = address(31L, "Cambuí");
    when(addresses.findAllById(any())).thenReturn(List.of(lucasHome, address(32L, null)));
    grids = new ArrayList<>();
    grids.add(gridDay(21L, DayOfWeek.MONDAY, SchoolShift.MORNING));
    grids.add(gridDay(21L, DayOfWeek.TUESDAY, SchoolShift.FULLTIME));
    grids.add(gridDay(22L, DayOfWeek.MONDAY, SchoolShift.MORNING));
    when(schoolDays.findByDependentIdIn(any())).thenReturn(grids);

    van = new VehicleModel();
    van.setId(60L);
    van.setToken("van");
    van.setDriver(carlos);
    van.setPlate("VAN1A23");
    van.setBrand("Renault");
    van.setModel("Master");
    van.setManufactureYear(2022);
    van.setColor("Branca");
    van.setCapacity(15);
    when(vehicles.findByToken("van")).thenReturn(Optional.of(van));
    when(vehicles.lockById(60L)).thenReturn(Optional.of(van));

    when(proposals.findByClientDriverIdAndStatus(40L, ProposalStatus.SENT))
        .thenReturn(Optional.empty());
    when(proposals.findTopByServiceRequestIdOrderByVersionDesc(50L)).thenReturn(Optional.empty());
    when(scheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    when(proposals.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ProposalModel proposal = call.getArgument(0);
              proposal.setId(70L);
              proposal.setToken("proposal");
              long itemId = 700;
              for (ProposalItemModel item : proposal.getItems()) {
                item.setId(++itemId);
              }
              when(proposals.findByTokenAndDriverId("proposal", 20L))
                  .thenReturn(Optional.of(proposal));
              return proposal;
            });

    sent = sentProposal();
    when(proposals.findByTokenAndClientId("sent", 10L)).thenReturn(Optional.of(sent));
    when(proposals.findByTokenAndDriverId("sent", 20L)).thenReturn(Optional.of(sent));
    when(requests.updateStatus(eq(50L), eq(Set.of(ServiceRequestStatus.ANSWERED)), any()))
        .thenReturn(1);
    when(requests.reopen(eq(50L), any())).thenReturn(1);
    when(proposals.accept(eq(71L), any()))
        .thenAnswer(
            call -> {
              sent.setStatus(ProposalStatus.ACCEPTED);
              sent.setAcceptedAt(call.getArgument(1));
              return 1;
            });
    when(proposals.decline(eq(71L), any()))
        .thenAnswer(
            call -> {
              sent.setStatus(ProposalStatus.DECLINED);
              sent.setDeclineReason(call.getArgument(1));
              return 1;
            });
    when(proposals.updateStatus(eq(71L), eq(ProposalStatus.SENT), any()))
        .thenAnswer(
            call -> {
              sent.setStatus(call.getArgument(2));
              return 1;
            });
    when(proposals.touchSent(71L)).thenReturn(1);
  }

  @Test
  void theFirstProposalIsVersionOneAndAnswersTheRequest() {
    Instant before = Instant.now();

    ProposalResponseDTO response = service.send(CARLOS_UID, "request", validBody());

    verify(requests).answer(eq(50L), any());
    verify(proposals, never()).updateStatus(anyLong(), any(), any());
    verify(proposals).saveAndFlush(savedProposal.capture());
    ProposalModel saved = savedProposal.getValue();
    assertThat(saved.getVersion()).isEqualTo(1);
    assertThat(saved.getParentProposalId()).isNull();
    assertThat(saved.getStatus()).isEqualTo(ProposalStatus.SENT);
    assertThat(saved.getClientDriver()).isSameAs(link);
    assertThat(saved.getVehicleId()).isEqualTo(60L);
    assertThat(saved.getMessage()).isEqualTo("Saio às 6h");
    assertThat(saved.getValidUntil())
        .isCloseTo(before.plus(Duration.ofDays(7)), within(5, ChronoUnit.SECONDS));
    assertThat(saved.getItems())
        .extracting(
            item -> item.getDependentId(),
            item -> item.getMonthlyAmount(),
            item -> item.getSchedule().getSlots().size())
        .containsExactlyInAnyOrder(
            tuple(21L, new BigDecimal("350.00"), 2), tuple(22L, new BigDecimal("300.00"), 2));
    assertThat(response.version()).isEqualTo(1);
    assertThat(response.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(response.serviceRequestToken()).isEqualTo("request");
    assertThat(response.vehicle().plate()).isEqualTo("VAN1A23");
  }

  @Test
  void theValidityComesFromValidDays() {
    Instant before = Instant.now();

    service.send(CARLOS_UID, "request", bodyWithValidDays(2));

    verify(proposals).saveAndFlush(savedProposal.capture());
    assertThat(savedProposal.getValue().getValidUntil())
        .isCloseTo(before.plus(Duration.ofDays(2)), within(5, ChronoUnit.SECONDS));
  }

  @Test
  void aNewVersionSupersedesTheCurrentOneAndPointsToIt() {
    request.setStatus(ServiceRequestStatus.ANSWERED);
    ProposalModel first = existingProposal(80L, 1, ProposalStatus.SENT);
    when(proposals.findByClientDriverIdAndStatus(40L, ProposalStatus.SENT))
        .thenReturn(Optional.of(first));
    when(proposals.findTopByServiceRequestIdOrderByVersionDesc(50L)).thenReturn(Optional.of(first));
    when(proposals.updateStatus(80L, ProposalStatus.SENT, ProposalStatus.SUPERSEDED)).thenReturn(1);

    ProposalResponseDTO response = service.send(CARLOS_UID, "request", validBody());

    verify(proposals).updateStatus(80L, ProposalStatus.SENT, ProposalStatus.SUPERSEDED);
    verify(proposals).saveAndFlush(savedProposal.capture());
    assertThat(savedProposal.getValue().getVersion()).isEqualTo(2);
    assertThat(savedProposal.getValue().getParentProposalId()).isEqualTo(80L);
    assertThat(response.version()).isEqualTo(2);
  }

  @Test
  void aVersionAfterAWithdrawnOneHasItAsParentAndSupersedesNothing() {
    ProposalModel withdrawn = existingProposal(80L, 3, ProposalStatus.WITHDRAWN);
    when(proposals.findTopByServiceRequestIdOrderByVersionDesc(50L))
        .thenReturn(Optional.of(withdrawn));

    service.send(CARLOS_UID, "request", validBody());

    verify(proposals, never()).updateStatus(anyLong(), any(), any());
    verify(proposals).saveAndFlush(savedProposal.capture());
    assertThat(savedProposal.getValue().getVersion()).isEqualTo(4);
    assertThat(savedProposal.getValue().getParentProposalId()).isEqualTo(80L);
  }

  @Test
  void theDriverSeesTheDependentsByFirstNameOnly() {
    ProposalResponseDTO response = service.send(CARLOS_UID, "request", validBody());

    assertThat(response.items())
        .extracting(
            item -> item.dependentToken(),
            item -> item.dependentName(),
            item -> item.dependentFirstName())
        .containsExactly(tuple("lucas", null, "Lucas"), tuple("marina", null, "Marina"));
  }

  @Test
  void bothPartiesSeeTheVehicleOfTheCopyAfterTheDriverEditsIt() {
    van.setPlate("VAN9Z99");
    van.setColor("Prata");

    ProposalResponseDTO client = service.findForClient(ANA_UID, "sent");
    ProposalResponseDTO driver = service.findForDriver(CARLOS_UID, "sent");

    assertThat(client.vehicle().plate()).isEqualTo("VAN1A23");
    assertThat(client.vehicle().color()).isEqualTo("Branca");
    assertThat(driver.vehicle().plate()).isEqualTo("VAN1A23");
    assertThat(client.items())
        .extracting(item -> item.dependentName())
        .containsExactly("Lucas Souza", "Marina Souza");
  }

  @Test
  void aCallerOfTheWrongTypeCannotSend() {
    when(users.requireByTokenAndType(ANA_UID, UserType.DRIVER))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "user.type.forbidden"));

    expectRefusal(
        () -> service.send(ANA_UID, "request", validBody()),
        HttpStatus.FORBIDDEN,
        "user.type.forbidden");
  }

  @Test
  void aRequestOfAnotherDriverIsNotFound() {
    expectRefusal(
        () -> service.send(CARLOS_UID, "other", validBody()),
        HttpStatus.NOT_FOUND,
        "service_request.not_found");
  }

  @Test
  void aClosedRequestTakesNoProposal() {
    for (ServiceRequestStatus closed :
        List.of(
            ServiceRequestStatus.CONVERTED,
            ServiceRequestStatus.DECLINED,
            ServiceRequestStatus.EXPIRED,
            ServiceRequestStatus.WITHDRAWN)) {
      request.setStatus(closed);

      expectRefusal(
          () -> service.send(CARLOS_UID, "request", validBody()),
          HttpStatus.CONFLICT,
          "proposal.request.not_open");
    }
  }

  @Test
  void anOverdueOpenRequestTakesNoProposalBeforeTheSweep() {
    request.setExpiresAt(Instant.now().minusSeconds(60));

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.CONFLICT,
        "proposal.request.not_open");
  }

  @Test
  void aRequestClosedWhileSendingTakesNoProposal() {
    when(requests.answer(eq(50L), any())).thenReturn(0);

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.CONFLICT,
        "proposal.request.not_open");
  }

  @Test
  void anEndBeforeTheStartIsRefused() {
    expectRefusal(
        () -> service.send(CARLOS_UID, "request", bodyWithPeriod(STARTS_ON, STARTS_ON)),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.period.invalid");
  }

  @Test
  void aPeriodLongerThanTwelveMonthsIsRefused() {
    expectRefusal(
        () ->
            service.send(
                CARLOS_UID,
                "request",
                bodyWithPeriod(STARTS_ON, STARTS_ON.plusMonths(12).plusDays(1))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.period.too_long");
  }

  @Test
  void itemsForOnlyOneOfTheTwoDependentsAreRefused() {
    expectRefusal(
        () -> service.send(CARLOS_UID, "request", bodyWithItems(item("lucas", "350.00"))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.items.mismatch");
  }

  @Test
  void anItemForADependentOutsideTheRequestIsRefused() {
    expectRefusal(
        () ->
            service.send(
                CARLOS_UID,
                "request",
                bodyWithItems(item("lucas", "350.00"), item("marina", "300.00"), item("teo", "1"))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.items.mismatch");
  }

  @Test
  void theSameDependentTwiceIsRefused() {
    expectRefusal(
        () ->
            service.send(
                CARLOS_UID,
                "request",
                bodyWithItems(item("lucas", "350.00"), item("lucas", "300.00"))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.items.mismatch");
  }

  @Test
  void aDependentRemovedSinceTheRequestIsRefused() {
    when(dependents.findAllById(List.of(21L, 22L))).thenReturn(List.of(lucas));

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.item.dependent_unavailable");
  }

  @Test
  void anUnknownVehicleIsNotFound() {
    expectRefusal(
        () -> service.send(CARLOS_UID, "request", bodyWithVehicle("unknown")),
        HttpStatus.NOT_FOUND,
        "proposal.vehicle.not_found");
  }

  @Test
  void anInactiveVehicleIsNotFound() {
    van.setActive(false);

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.NOT_FOUND,
        "proposal.vehicle.not_found");
  }

  @Test
  void aVehicleOfAnotherDriverIsNotFound() {
    DriverModel joana = new DriverModel();
    joana.setId(23L);
    van.setDriver(joana);

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.NOT_FOUND,
        "proposal.vehicle.not_found");
  }

  @Test
  void aRepeatedWeekdayAndLegIsRefused() {
    ScheduleSlotRequestDTO monday = slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, null);

    expectRefusal(
        () ->
            service.send(
                CARLOS_UID,
                "request",
                bodyWithItems(
                    new ProposalItemRequestDTO(
                        "lucas", new BigDecimal("350"), List.of(monday, monday)),
                    item("marina", "300.00"))),
        HttpStatus.BAD_REQUEST,
        "schedule.slot.duplicate");
  }

  @Test
  void aWindowThatEndsBeforeItStartsIsRefused() {
    ScheduleSlotRequestDTO backwards =
        slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, LocalTime.of(6, 0));

    expectRefusal(
        () ->
            service.send(
                CARLOS_UID,
                "request",
                bodyWithItems(
                    new ProposalItemRequestDTO("lucas", new BigDecimal("350"), List.of(backwards)),
                    item("marina", "300.00"))),
        HttpStatus.BAD_REQUEST,
        "schedule.slot.window_invalid");
  }

  @Test
  void losingTheRaceOnTheSentIndexIsAConflict() {
    doThrow(new DataIntegrityViolationException("sent")).when(proposals).saveAndFlush(any());

    assertThatThrownBy(() -> service.send(CARLOS_UID, "request", validBody()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.getReason()).isEqualTo("proposal.sent_conflict");
            });
  }

  @Test
  void aVersionSupersededMeanwhileIsAConflict() {
    ProposalModel first = existingProposal(80L, 1, ProposalStatus.SENT);
    when(proposals.findByClientDriverIdAndStatus(40L, ProposalStatus.SENT))
        .thenReturn(Optional.of(first));
    when(proposals.updateStatus(80L, ProposalStatus.SENT, ProposalStatus.SUPERSEDED)).thenReturn(0);

    expectRefusal(
        () -> service.send(CARLOS_UID, "request", validBody()),
        HttpStatus.CONFLICT,
        "proposal.sent_conflict");
  }

  @Test
  void theChainComesOldestFirstWithTheAdjustmentsOfEachVersion() {
    ProposalModel first = existingProposal(80L, 1, ProposalStatus.SUPERSEDED);
    ProposalAdjustmentModel price = new ProposalAdjustmentModel();
    price.setProposalId(80L);
    price.setField(ProposalAdjustmentField.PRICE);
    price.setSuggestedValue("320,00");
    when(proposals.findChainByServiceRequestId(50L)).thenReturn(List.of(first, sent));
    when(adjustments.findByProposalIdInOrderByCreatedAtAscIdAsc(List.of(80L, 71L)))
        .thenReturn(List.of(price));

    List<ProposalResponseDTO> chain = service.findChainForClient(ANA_UID, "request");

    assertThat(chain).extracting(proposal -> proposal.version()).containsExactly(1, 2);
    assertThat(chain.getFirst().adjustments())
        .extracting(adjustment -> adjustment.suggestedValue())
        .containsExactly("320,00");
    assertThat(chain.getLast().adjustments()).isEmpty();
    assertThat(service.findChainForDriver(CARLOS_UID, "request").getLast().items())
        .extracting(item -> item.dependentName())
        .containsOnlyNulls();
  }

  @Test
  void anotherClientsRequestHasNoChain() {
    expectRefusal(
        () -> service.findChainForClient(ANA_UID, "other"),
        HttpStatus.NOT_FOUND,
        "service_request.not_found");
  }

  @Test
  void anotherClientsProposalIsNotFound() {
    expectRefusal(
        () -> service.accept(ANA_UID, "other"), HttpStatus.NOT_FOUND, "proposal.not_found");
  }

  @Test
  void theClientAcceptsAndTheRequestIsConverted() {
    ProposalResponseDTO response = service.accept(ANA_UID, "sent");

    verify(requests)
        .updateStatus(50L, Set.of(ServiceRequestStatus.ANSWERED), ServiceRequestStatus.CONVERTED);
    verify(proposals).accept(eq(71L), any());
    assertThat(response.status()).isEqualTo(ProposalStatus.ACCEPTED);
    assertThat(response.acceptedAt()).isNotNull();
  }

  @Test
  void theDependentsAndTheVehicleAreLockedBeforeTheAccept() {
    service.accept(ANA_UID, "sent");

    verify(dependents).lockByIdIn(List.of(21L, 22L));
    verify(vehicles).lockById(60L);
  }

  @Test
  void acceptingAfterTheValidityIsRefused() {
    sent.setValidUntil(Instant.now().minusSeconds(1));

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.expired");
  }

  @Test
  void acceptingAVersionThatIsNotCurrentIsRefused() {
    sent.setStatus(ProposalStatus.SUPERSEDED);

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.not_current");
  }

  @Test
  void acceptingARequestClosedMeanwhileIsRefused() {
    when(requests.updateStatus(eq(50L), eq(Set.of(ServiceRequestStatus.ANSWERED)), any()))
        .thenReturn(0);

    expectRefusal(
        () -> service.accept(ANA_UID, "sent"), HttpStatus.CONFLICT, "proposal.not_current");
    verify(proposals, never()).accept(anyLong(), any());
  }

  @Test
  void acceptingAProposalReplacedMeanwhileIsRefused() {
    when(proposals.accept(eq(71L), any())).thenReturn(0);

    assertThatThrownBy(() -> service.accept(ANA_UID, "sent"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getReason()).isEqualTo("proposal.not_current"));
  }

  @Test
  void acceptingAfterADependentWasRemovedIsStale() {
    when(dependents.lockByIdIn(List.of(21L, 22L))).thenReturn(List.of(lucas));

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterTheFamilyMovedIsStale() {
    lucasHome.setNeighborhood("Taquaral");

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterTheSchoolChangedIsStale() {
    lucas.setSchoolId(8L);

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterARequestedDayChangedPeriodIsStale() {
    grids.get(1).setSchoolShift(SchoolShift.MORNING);

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterTheDriverChangedThePlateIsStale() {
    van.setPlate("VAN9Z99");

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
    assertThat(service.findForClient(ANA_UID, "sent").vehicle().plate()).isEqualTo("VAN1A23");
  }

  @Test
  void acceptingAfterTheVehicleWasRemovedIsStale() {
    when(vehicles.lockById(60L)).thenReturn(Optional.empty());

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterTheDriverWasDeactivatedIsStale() {
    carlos.setActive(false);

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void acceptingAfterTheDriverWasRejectedIsStale() {
    carlos.setApprovalStatus(DriverApprovalStatus.REJECTED);

    expectNothingAccepted(HttpStatus.CONFLICT, "proposal.accept.stale");
  }

  @Test
  void aCorrectedNameOrBirthDateDoesNotBlockTheAccept() {
    lucas.setName("Lucas de Souza");
    lucas.setBirthDate(LocalDate.of(2016, 4, 3));

    assertThat(service.accept(ANA_UID, "sent").status()).isEqualTo(ProposalStatus.ACCEPTED);
  }

  @Test
  void theClientDeclinesAndTheRequestIsWithdrawn() {
    ProposalResponseDTO response =
        service.decline(ANA_UID, "sent", new ProposalDeclineRequestDTO("  Caro demais  "));

    verify(requests)
        .updateStatus(50L, Set.of(ServiceRequestStatus.ANSWERED), ServiceRequestStatus.WITHDRAWN);
    verify(proposals).decline(71L, "Caro demais");
    assertThat(response.status()).isEqualTo(ProposalStatus.DECLINED);
    assertThat(response.declineReason()).isEqualTo("Caro demais");
  }

  @Test
  void declinesWithoutAReason() {
    service.decline(ANA_UID, "sent", null);

    verify(proposals).decline(71L, null);
  }

  @Test
  void decliningAVersionThatIsNotCurrentIsRefused() {
    sent.setStatus(ProposalStatus.EXPIRED);

    expectRefusal(
        () -> service.decline(ANA_UID, "sent", null), HttpStatus.CONFLICT, "proposal.not_current");
    verify(requests, never()).updateStatus(anyLong(), any(), any());
    verify(proposals, never()).decline(anyLong(), any());
  }

  @Test
  void theClientAsksToLowerThePrice() {
    ProposalAdjustmentModel stored = new ProposalAdjustmentModel();
    stored.setProposalId(71L);
    stored.setField(ProposalAdjustmentField.PRICE);
    stored.setSuggestedValue("320,00");
    when(adjustments.findByProposalIdInOrderByCreatedAtAscIdAsc(List.of(71L)))
        .thenReturn(List.of(stored));

    ProposalResponseDTO response =
        service.requestAdjustment(
            ANA_UID,
            "sent",
            new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.PRICE, " 320,00 ", "  "));

    verify(proposals).touchSent(71L);
    verify(adjustments).save(savedAdjustment.capture());
    assertThat(savedAdjustment.getValue().getProposalId()).isEqualTo(71L);
    assertThat(savedAdjustment.getValue().getSuggestedValue()).isEqualTo("320,00");
    assertThat(savedAdjustment.getValue().getNote()).isNull();
    assertThat(response.status()).isEqualTo(ProposalStatus.SENT);
    assertThat(response.adjustments())
        .extracting(adjustment -> adjustment.field(), adjustment -> adjustment.suggestedValue())
        .containsExactly(tuple(ProposalAdjustmentField.PRICE, "320,00"));
  }

  @Test
  void anAdjustmentWithNeitherValueNorNoteIsRefused() {
    expectRefusal(
        () ->
            service.requestAdjustment(
                ANA_UID,
                "sent",
                new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.OTHER, " ", null)),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "proposal.adjustment.empty");
    verify(adjustments, never()).save(any());
  }

  @Test
  void aDriverIsForbiddenBeforeTheAdjustmentIsChecked() {
    when(users.requireByTokenAndType(CARLOS_UID, UserType.CLIENT))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "user.type.forbidden"));

    expectRefusal(
        () ->
            service.requestAdjustment(
                CARLOS_UID,
                "sent",
                new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.OTHER, null, null)),
        HttpStatus.FORBIDDEN,
        "user.type.forbidden");
  }

  @Test
  void anAdjustmentOnAVersionReplacedMeanwhileIsRefused() {
    when(proposals.touchSent(71L)).thenReturn(0);

    expectRefusal(
        () ->
            service.requestAdjustment(
                ANA_UID,
                "sent",
                new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.SCHEDULE, null, "Só ida")),
        HttpStatus.CONFLICT,
        "proposal.not_current");
    verify(adjustments, never()).save(any());
  }

  @Test
  void theDriverWithdrawsAndTheRequestReopensWithANewDeadline() {
    Instant before = Instant.now();
    ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);

    ProposalResponseDTO response = service.withdraw(CARLOS_UID, "sent");

    verify(requests).reopen(eq(50L), expiresAt.capture());
    assertThat(expiresAt.getValue())
        .isCloseTo(before.plus(Duration.ofHours(72)), within(5, ChronoUnit.SECONDS));
    verify(proposals).updateStatus(71L, ProposalStatus.SENT, ProposalStatus.WITHDRAWN);
    assertThat(response.status()).isEqualTo(ProposalStatus.WITHDRAWN);
  }

  @Test
  void withdrawingAVersionThatIsNotCurrentIsRefused() {
    sent.setStatus(ProposalStatus.ACCEPTED);

    expectRefusal(
        () -> service.withdraw(CARLOS_UID, "sent"), HttpStatus.CONFLICT, "proposal.not_current");
    verify(requests, never()).reopen(anyLong(), any());
  }

  @Test
  void withdrawingWhenTheRequestClosedMeanwhileIsRefused() {
    when(requests.reopen(eq(50L), any())).thenReturn(0);

    expectRefusal(
        () -> service.withdraw(CARLOS_UID, "sent"), HttpStatus.CONFLICT, "proposal.not_current");
    verify(proposals, never()).updateStatus(anyLong(), any(), any());
  }

  @Test
  void expiresTheOverdueProposalsAndReopensTheirRequests() {
    Instant now = Instant.now();
    when(proposals.findOverdueSentIds(now, Limit.of(200))).thenReturn(List.of(71L));
    when(proposals.findById(71L)).thenReturn(Optional.of(sent));

    int expired = service.expireOverdue(now, 200);

    assertThat(expired).isEqualTo(1);
    verify(requests).reopen(50L, now.plus(ServiceRequestService.ANSWER_WINDOW));
    verify(proposals).updateStatus(71L, ProposalStatus.SENT, ProposalStatus.EXPIRED);
  }

  @Test
  void aProposalThatChangedBeforeItsWriteIsSkippedAndTheNextOneExpires() {
    Instant now = Instant.now();
    ProposalModel acceptedMeanwhile = existingProposal(80L, 1, ProposalStatus.SENT);
    when(proposals.findOverdueSentIds(now, Limit.of(2))).thenReturn(List.of(80L, 71L));
    when(proposals.findById(80L)).thenReturn(Optional.of(acceptedMeanwhile));
    when(proposals.findById(71L)).thenReturn(Optional.of(sent));
    when(proposals.updateStatus(80L, ProposalStatus.SENT, ProposalStatus.EXPIRED)).thenReturn(0);

    int expired = service.expireOverdue(now, 2);

    assertThat(expired).isEqualTo(1);
    verify(proposals).updateStatus(71L, ProposalStatus.SENT, ProposalStatus.EXPIRED);
  }

  @Test
  void aProposalThatFailsUnexpectedlyIsSkippedAndTheNextOneExpires() {
    Instant now = Instant.now();
    ProposalModel broken = existingProposal(80L, 1, ProposalStatus.SENT);
    when(proposals.findOverdueSentIds(now, Limit.of(2))).thenReturn(List.of(80L, 71L));
    when(proposals.findById(80L)).thenReturn(Optional.of(broken));
    when(proposals.findById(71L)).thenReturn(Optional.of(sent));
    when(proposals.updateStatus(80L, ProposalStatus.SENT, ProposalStatus.EXPIRED))
        .thenThrow(new DataIntegrityViolationException("broken row"));

    int expired = service.expireOverdue(now, 2);

    assertThat(expired).isEqualTo(1);
    verify(proposals).updateStatus(71L, ProposalStatus.SENT, ProposalStatus.EXPIRED);
  }

  @Test
  void nothingOverdueExpiresNothing() {
    Instant now = Instant.now();
    when(proposals.findOverdueSentIds(now, Limit.of(200))).thenReturn(List.of());

    assertThat(service.expireOverdue(now, 200)).isZero();
    verify(requests, never()).reopen(any(), any());
    verify(proposals, never()).updateStatus(anyLong(), any(), any());
  }

  @Test
  void aSentProposalIsPublishedForTheClient() {
    service.send(CARLOS_UID, "request", validBody());

    verify(events).publishEvent(new ProposalEvent.Sent("proposal", ANA_UID));
  }

  @Test
  void anAcceptedProposalIsPublishedForTheDriver() {
    service.accept(ANA_UID, "sent");

    verify(events).publishEvent(new ProposalEvent.Accepted("sent", CARLOS_UID));
  }

  @Test
  void aDeclinedProposalIsPublishedForTheDriver() {
    service.decline(ANA_UID, "sent", null);

    verify(events).publishEvent(new ProposalEvent.Declined("sent", CARLOS_UID));
  }

  @Test
  void anAdjustmentIsPublishedForTheDriver() {
    service.requestAdjustment(
        ANA_UID,
        "sent",
        new ProposalAdjustmentRequestDTO(ProposalAdjustmentField.PRICE, "320,00", null));

    verify(events).publishEvent(new ProposalEvent.AdjustmentRequested("sent", CARLOS_UID));
  }

  @Test
  void withdrawalsExpirationsAndRefusalsAreNotPublished() {
    sent.setValidUntil(Instant.now().minusSeconds(60));
    expectRefusal(() -> service.accept(ANA_UID, "sent"), HttpStatus.CONFLICT, "proposal.expired");
    service.withdraw(CARLOS_UID, "sent");
    when(proposals.findOverdueSentIds(any(), any())).thenReturn(List.of(71L));
    when(proposals.findById(71L)).thenReturn(Optional.of(sent));
    service.expireOverdue(Instant.now(), 10);

    verifyNoInteractions(events);
  }

  private void expectNothingAccepted(HttpStatus status, String key) {
    expectRefusal(() -> service.accept(ANA_UID, "sent"), status, key);
    verify(proposals, never()).accept(anyLong(), any());
    verify(requests, never()).updateStatus(anyLong(), any(), any());
  }

  private void expectRefusal(Runnable call, HttpStatus status, String key) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(status);
              assertThat(error.getReason()).isEqualTo(key);
            });
    verify(proposals, never()).saveAndFlush(any());
    verify(adjustments, never()).save(any());
  }

  private ProposalCreateRequestDTO validBody() {
    return body(
        STARTS_ON,
        STARTS_ON.plusMonths(10),
        "van",
        null,
        List.of(item("lucas", "350.00"), item("marina", "300.00")));
  }

  private ProposalCreateRequestDTO bodyWithValidDays(int validDays) {
    return body(STARTS_ON, STARTS_ON.plusMonths(10), "van", validDays, validBody().items());
  }

  private ProposalCreateRequestDTO bodyWithPeriod(LocalDate startsOn, LocalDate endsOn) {
    return body(startsOn, endsOn, "van", null, validBody().items());
  }

  private ProposalCreateRequestDTO bodyWithVehicle(String vehicleToken) {
    return body(STARTS_ON, STARTS_ON.plusMonths(10), vehicleToken, null, validBody().items());
  }

  private ProposalCreateRequestDTO bodyWithItems(ProposalItemRequestDTO... items) {
    return body(STARTS_ON, STARTS_ON.plusMonths(10), "van", null, List.of(items));
  }

  private ProposalCreateRequestDTO body(
      LocalDate startsOn,
      LocalDate endsOn,
      String vehicleToken,
      Integer validDays,
      List<ProposalItemRequestDTO> items) {
    return new ProposalCreateRequestDTO(
        startsOn,
        endsOn,
        new BigDecimal("6500.00"),
        10,
        5,
        new BigDecimal("100.00"),
        ProposalAdjustmentPolicy.NONE,
        ProposalCancellationPolicy.NOTICE_30_DAYS,
        vehicleToken,
        validDays,
        "  Saio às 6h ",
        items);
  }

  private ProposalItemRequestDTO item(String dependentToken, String monthlyAmount) {
    return new ProposalItemRequestDTO(
        dependentToken,
        new BigDecimal(monthlyAmount),
        List.of(
            slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, null),
            slot(DayOfWeek.MONDAY, RouteLeg.RETURN, null)));
  }

  private ScheduleSlotRequestDTO slot(DayOfWeek weekday, RouteLeg leg, LocalTime windowEnd) {
    return new ScheduleSlotRequestDTO(
        weekday, leg, OperationShift.MORNING, LocalTime.of(6, 40), windowEnd);
  }

  private ProposalModel sentProposal() {
    ProposalModel proposal = existingProposal(71L, 2, ProposalStatus.SENT);
    proposal.setToken("sent");
    proposal.addItem(proposalItem(711L, 21L));
    proposal.addItem(proposalItem(712L, 22L));
    return proposal;
  }

  private ProposalModel existingProposal(Long id, int version, ProposalStatus status) {
    ProposalModel proposal = new ProposalModel();
    proposal.setId(id);
    proposal.setToken("proposal-" + id);
    proposal.setClientDriver(link);
    proposal.setServiceRequest(request);
    proposal.setVersion(version);
    proposal.setStatus(status);
    proposal.setValidUntil(Instant.now().plus(Duration.ofDays(7)));
    proposal.setStartsOn(STARTS_ON);
    proposal.setEndsOn(STARTS_ON.plusMonths(10));
    proposal.setTotalAmount(new BigDecimal("6500.00"));
    proposal.setInstallments(10);
    proposal.setDueDay(5);
    proposal.setAdjustmentPolicy(ProposalAdjustmentPolicy.NONE);
    proposal.setCancellationPolicy(ProposalCancellationPolicy.NOTICE_30_DAYS);
    proposal.setVehicleId(60L);
    proposal.setSnapshot(
        new ProposalModel.Snapshot(
            ProposalModel.Snapshot.CURRENT_VERSION,
            new ProposalModel.Snapshot.Vehicle(
                "VAN1A23", "Renault", "Master", 2022, "Branca", 15)));
    return proposal;
  }

  private ProposalItemModel proposalItem(Long id, Long dependentId) {
    ProposalItemModel item = new ProposalItemModel();
    item.setId(id);
    item.setDependentId(dependentId);
    item.setMonthlyAmount(new BigDecimal("350.00"));
    item.setSchedule(new ScheduleModel());
    return item;
  }

  private ServiceRequestItemModel requestItem(
      Long id, Long dependentId, String name, String neighborhood, SchoolShift... shifts) {
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setId(id);
    item.setDependentId(dependentId);
    item.setSchoolId(7L);
    item.setPickupCityId(5L);
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent(name, LocalDate.of(2016, 4, 2)),
            new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
            new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", neighborhood)));
    for (int index = 0; index < shifts.length; index++) {
      ServiceRequestDayModel day = new ServiceRequestDayModel();
      day.setWeekday(DayOfWeek.of(index + 1));
      day.setSchoolShift(shifts[index]);
      day.setNeedsOutbound(true);
      day.setNeedsReturn(true);
      item.addDay(day);
    }
    return item;
  }

  private DependentModel dependent(Long id, String name, Long addressId) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setName(name);
    dependent.setSchoolId(7L);
    dependent.setAddressId(addressId);
    return dependent;
  }

  private AddressModel address(Long id, String neighborhood) {
    CityModel campinas = new CityModel();
    campinas.setId(5L);
    AddressModel address = new AddressModel();
    address.setId(id);
    address.setCity(campinas);
    address.setNeighborhood(neighborhood);
    return address;
  }

  private DependentSchoolDayModel gridDay(Long dependentId, DayOfWeek weekday, SchoolShift shift) {
    DependentSchoolDayModel day = new DependentSchoolDayModel();
    day.setDependentId(dependentId);
    day.setWeekday(weekday);
    day.setSchoolShift(shift);
    return day;
  }

  private UserModel user(Long id, String name, UserType type) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setName(name);
    user.setType(type);
    return user;
  }

  private record IdAndToken(Long getId, String getToken)
      implements DependentRepository.IdAndToken {}
}
