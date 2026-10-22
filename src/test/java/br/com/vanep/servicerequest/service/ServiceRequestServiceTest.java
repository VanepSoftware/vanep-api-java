package br.com.vanep.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.service.ClientDriverService;
import br.com.vanep.contract.enums.ContractStatus;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.contractreadiness.service.ContractReadinessService;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.model.DependentSchoolDayModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestCreateRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDeclineRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestServiceTest {

  private static final String ANA_UID = "ana-uid";
  private static final String CARLOS_UID = "carlos-uid";
  private static final LocalDate DESIRED_START = LocalDate.now().plusMonths(1);
  private static final LocalDate LUCAS_BIRTH_DATE = LocalDate.of(2016, 4, 2);
  private static final List<DayOfWeek> MONDAY_TO_FRIDAY =
      List.copyOf(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));

  @Mock private ServiceRequestRepository requests;
  @Mock private ProposalRepository proposals;
  @Mock private ClientDriverService linkService;
  @Mock private ContractRepository contracts;
  @Mock private ContractReadinessService readiness;
  @Mock private UserService users;
  @Mock private ClientRepository clients;
  @Mock private DriverRepository drivers;
  @Mock private DependentRepository dependents;
  @Mock private DependentSchoolDayRepository schoolDays;
  @Mock private SchoolRepository schools;
  @Mock private AddressRepository addresses;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<ServiceRequestModel> savedRequest;

  private ServiceRequestService service;
  private UserModel anaUser;
  private ClientModel ana;
  private DriverModel carlos;
  private ClientDriverModel link;
  private DependentModel lucas;
  private DependentModel marina;
  private List<DependentSchoolDayModel> grids;

  @BeforeEach
  void setUp() {
    service =
        new ServiceRequestService(
            requests,
            proposals,
            linkService,
            contracts,
            readiness,
            users,
            clients,
            drivers,
            dependents,
            schoolDays,
            schools,
            addresses,
            new ServiceRequestItemsPolicy(),
            new ServiceRequestTransitionPolicy(),
            new ServiceRequestMapper(),
            messages);
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));

    anaUser = user(1L, "Ana Souza", UserType.CLIENT);
    ana = new ClientModel();
    ana.setId(10L);
    ana.setToken("ana");
    ana.setUser(anaUser);
    when(users.requireByTokenAndType(ANA_UID, UserType.CLIENT)).thenReturn(anaUser);
    when(clients.findByUserId(1L)).thenReturn(Optional.of(ana));

    UserModel carlosUser = user(2L, "Carlos Pereira", UserType.DRIVER);
    carlos = new DriverModel();
    carlos.setId(20L);
    carlos.setToken("carlos");
    carlos.setUser(carlosUser);
    carlos.setAvailable(true);
    when(drivers.findSearchableByToken("carlos")).thenReturn(Optional.of(carlos));
    when(users.requireByTokenAndType(CARLOS_UID, UserType.DRIVER)).thenReturn(carlosUser);
    when(drivers.findByUserId(2L)).thenReturn(Optional.of(carlos));

    link = new ClientDriverModel();
    link.setId(40L);
    link.setToken("link");
    link.setClient(ana);
    link.setDriver(carlos);
    link.setStatus(RelationshipStatus.PENDING);
    when(linkService.findOrCreateForRequest(ana, carlos)).thenReturn(link);
    when(contracts.findStatusesByClientDriverId(40L)).thenReturn(List.of());
    when(requests.findByClientDriverIdAndStatusIn(40L, ServiceRequestStatus.OPEN_STATUSES))
        .thenReturn(Optional.empty());
    when(requests.countByClientIdAndStatusIn(10L, ServiceRequestStatus.OPEN_STATUSES))
        .thenReturn(0L);
    AtomicLong ids = new AtomicLong(100);
    when(requests.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              ServiceRequestModel request = call.getArgument(0);
              request.setToken("request");
              request.getItems().forEach(item -> item.setId(ids.incrementAndGet()));
              return request;
            });

    SchoolModel school = new SchoolModel();
    school.setId(7L);
    school.setName("Escola Municipal");
    lucas = dependent(21L, "lucas", "Lucas Souza", LUCAS_BIRTH_DATE, 31L);
    marina = dependent(22L, "marina", "Marina Souza", LocalDate.of(2019, 9, 15), 32L);
    when(dependents.lockByTokenInAndClientId(List.of("lucas", "marina"), 10L))
        .thenReturn(List.of(lucas, marina));
    when(dependents.lockByTokenInAndClientId(List.of("lucas"), 10L)).thenReturn(List.of(lucas));
    when(schools.findAllById(any())).thenReturn(List.of(school));
    when(addresses.findAllById(any()))
        .thenReturn(List.of(address(31L, "Cambuí"), address(32L, null)));
    grids = new ArrayList<>();
    grids.addAll(
        grid(
            21L,
            SchoolShift.MORNING,
            SchoolShift.FULLTIME,
            SchoolShift.MORNING,
            SchoolShift.FULLTIME,
            SchoolShift.MORNING));
    grids.addAll(
        grid(
            22L,
            SchoolShift.MORNING,
            SchoolShift.MORNING,
            SchoolShift.MORNING,
            SchoolShift.MORNING,
            SchoolShift.MORNING));
    when(schoolDays.findByDependentIdIn(any())).thenReturn(grids);
    when(readiness.isReady(any(), any(), any())).thenReturn(true);
  }

  @Test
  void sendsARequestForTwoSiblingsFromMondayToFriday() {
    Instant before = Instant.now();

    ServiceRequestClientResponseDTO response =
        service.send(
            ANA_UID,
            request(
                item("lucas", roundTrip(MONDAY_TO_FRIDAY)),
                item("marina", roundTrip(MONDAY_TO_FRIDAY))));

    assertThat(response.status()).isEqualTo(ServiceRequestStatus.OPEN);
    assertThat(response.expiresAt())
        .isCloseTo(before.plus(Duration.ofHours(72)), within(5, ChronoUnit.SECONDS));
    assertThat(response.clientDriverToken()).isEqualTo("link");
    assertThat(response.items())
        .extracting(item -> item.dependentToken(), item -> item.days().size())
        .containsExactly(tuple("lucas", 5), tuple("marina", 5));
    verify(requests).saveAndFlush(savedRequest.capture());
    assertThat(savedRequest.getValue().getClientDriver()).isSameAs(link);
    assertThat(savedRequest.getValue().getDesiredStartDate()).isEqualTo(DESIRED_START);
    assertThat(savedRequest.getValue().getNotes()).isEqualTo("Portão azul");
  }

  @Test
  void eachItemCopiesTheDependentSchoolAndPickupAndKeepsTheIds() {
    service.send(ANA_UID, request(item("lucas", roundTrip(List.of(DayOfWeek.MONDAY)))));

    verify(requests).saveAndFlush(savedRequest.capture());
    ServiceRequestItemModel item = savedRequest.getValue().getItems().iterator().next();
    assertThat(item.getDependentId()).isEqualTo(21L);
    assertThat(item.getSchoolId()).isEqualTo(7L);
    assertThat(item.getPickupCityId()).isEqualTo(30L);
    assertThat(item.getSnapshot())
        .isEqualTo(
            new ServiceRequestItemModel.Snapshot(
                ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
                new ServiceRequestItemModel.Snapshot.Dependent("Lucas Souza", LUCAS_BIRTH_DATE),
                new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
                new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", "Cambuí")));
  }

  @Test
  void eachDayCopiesThePeriodOfTheGridWithTheLegsTheClientSent() {
    service.send(
        ANA_UID,
        request(
            item(
                "lucas",
                List.of(
                    day(DayOfWeek.TUESDAY, true, true),
                    day(DayOfWeek.WEDNESDAY, true, false),
                    day(DayOfWeek.MONDAY, true, true)))));

    verify(requests).saveAndFlush(savedRequest.capture());
    assertThat(savedRequest.getValue().getItems().iterator().next().getDays())
        .extracting(
            day -> day.getWeekday(),
            day -> day.getSchoolShift(),
            day -> day.isNeedsOutbound(),
            day -> day.isNeedsReturn())
        .containsExactlyInAnyOrder(
            tuple(DayOfWeek.MONDAY, SchoolShift.MORNING, true, true),
            tuple(DayOfWeek.TUESDAY, SchoolShift.FULLTIME, true, true),
            tuple(DayOfWeek.WEDNESDAY, SchoolShift.MORNING, true, false));
  }

  @Test
  void changingTheGridAfterSendingDoesNotChangeTheRequest() {
    service.send(ANA_UID, request(item("lucas", List.of(day(DayOfWeek.THURSDAY, true, true)))));
    grids.forEach(day -> day.setSchoolShift(SchoolShift.NIGHT));

    verify(requests).saveAndFlush(savedRequest.capture());
    assertThat(savedRequest.getValue().getItems().iterator().next().getDays())
        .extracting(day -> day.getSchoolShift())
        .containsExactly(SchoolShift.FULLTIME);
  }

  @Test
  void theDependentsOfTheRequestAreLockedNotJustRead() {
    service.send(ANA_UID, request(item("lucas", List.of(day(DayOfWeek.MONDAY, true, true)))));

    verify(dependents).lockByTokenInAndClientId(List.of("lucas"), 10L);
    verify(dependents, never()).findByToken(anyString());
  }

  @Test
  void anUnknownInactiveOrUnapprovedDriverIsNotFound() {
    assertRefused(
        () ->
            service.send(
                ANA_UID,
                new ServiceRequestCreateRequestDTO(
                    "ghost", DESIRED_START, null, List.of(item("lucas", mondayRoundTrip())))),
        HttpStatus.NOT_FOUND,
        "service_request.driver.not_found");
    assertNothingWritten();
  }

  @Test
  void aDriverWhoTakesNoNewStudentsIsRefused() {
    carlos.setAvailable(false);

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "service_request.driver.not_available");
    assertNothingWritten();
  }

  @Test
  void aDependentOfAnotherClientIsNotFound() {
    when(dependents.lockByTokenInAndClientId(List.of("lucas", "stranger"), 10L))
        .thenReturn(List.of(lucas));

    assertRefused(
        () ->
            service.send(
                ANA_UID,
                request(item("lucas", mondayRoundTrip()), item("stranger", mondayRoundTrip()))),
        HttpStatus.NOT_FOUND,
        "service_request.dependent.not_found");
    assertNothingWritten();
  }

  @Test
  void aWeekdayOutsideTheGridIsRefused() {
    assertRefused(
        () ->
            service.send(
                ANA_UID, request(item("lucas", List.of(day(DayOfWeek.SATURDAY, true, true))))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "service_request.day.no_school");
    assertNothingWritten();
  }

  @Test
  void missingContractDataIsRefusedBeforeTheLinkExists() {
    when(readiness.isReady(eq(anaUser), eq(List.of(lucas)), any())).thenReturn(false);

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "service_request.not_ready");
    assertNothingWritten();
  }

  @Test
  void aBlockedLinkIsRefused() {
    link.setStatus(RelationshipStatus.BLOCKED);

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "service_request.link.blocked");
    verify(requests, never()).saveAndFlush(any());
  }

  @Test
  void aPairWithASignedContractNotEndedIsRefused() {
    for (ContractStatus status : ContractStatus.SIGNED_AND_NOT_ENDED) {
      when(contracts.findStatusesByClientDriverId(40L))
          .thenReturn(List.of(ContractStatus.ENDED, status));

      assertRefused(
          () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
          HttpStatus.UNPROCESSABLE_CONTENT,
          "service_request.link.has_contract");
    }
    verify(requests, never()).saveAndFlush(any());
  }

  @Test
  void aPairWhoseContractsEndedCanAskAgain() {
    when(contracts.findStatusesByClientDriverId(40L))
        .thenReturn(List.of(ContractStatus.ENDED, ContractStatus.CANCELLED));

    service.send(ANA_UID, request(item("lucas", mondayRoundTrip())));

    verify(requests).saveAndFlush(any());
  }

  @Test
  void aSecondOpenRequestOfThePairIsAConflict() {
    when(requests.findByClientDriverIdAndStatusIn(40L, ServiceRequestStatus.OPEN_STATUSES))
        .thenReturn(Optional.of(new ServiceRequestModel()));

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.CONFLICT,
        "service_request.open_exists");
    verify(requests, never()).saveAndFlush(any());
  }

  @Test
  void aConcurrentRequestThatWinsThePairIndexIsAConflict() {
    doThrow(new DataIntegrityViolationException("service_request_client_driver_open_key"))
        .when(requests)
        .saveAndFlush(any());

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.CONFLICT,
        "service_request.open_exists");
  }

  @Test
  void aConcurrentFirstRequestThatWinsTheLinkIsAConflict() {
    when(linkService.findOrCreateForRequest(ana, carlos))
        .thenThrow(new DataIntegrityViolationException("client_driver_pair_active_key"));

    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.CONFLICT,
        "service_request.open_exists");
    verify(requests, never()).saveAndFlush(any());
  }

  @Test
  void theFifthOpenRequestPassesAndTheSixthIsRefused() {
    when(requests.countByClientIdAndStatusIn(10L, ServiceRequestStatus.OPEN_STATUSES))
        .thenReturn(4L);
    service.send(ANA_UID, request(item("lucas", mondayRoundTrip())));
    verify(requests).saveAndFlush(any());

    when(requests.countByClientIdAndStatusIn(10L, ServiceRequestStatus.OPEN_STATUSES))
        .thenReturn(5L);
    assertRefused(
        () -> service.send(ANA_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.UNPROCESSABLE_CONTENT,
        "service_request.open_limit");
  }

  @Test
  void theSameDependentTwiceIsABadRequestBeforeAnyLookup() {
    assertRefused(
        () ->
            service.send(
                ANA_UID,
                request(item("lucas", mondayRoundTrip()), item("lucas", mondayRoundTrip()))),
        HttpStatus.BAD_REQUEST,
        "service_request.item.dependent_duplicate");
    verifyNoInteractions(drivers, dependents, linkService);
  }

  @Test
  void theSameWeekdayTwiceInAnItemIsABadRequest() {
    assertRefused(
        () ->
            service.send(
                ANA_UID,
                request(
                    item(
                        "lucas",
                        List.of(
                            day(DayOfWeek.MONDAY, true, false),
                            day(DayOfWeek.MONDAY, false, true))))),
        HttpStatus.BAD_REQUEST,
        "service_request.day.weekday_duplicate");
    assertNothingWritten();
  }

  @Test
  void aDayWithNeitherLegIsABadRequest() {
    assertRefused(
        () ->
            service.send(
                ANA_UID, request(item("lucas", List.of(day(DayOfWeek.MONDAY, false, false))))),
        HttpStatus.BAD_REQUEST,
        "service_request.day.leg_required");
    assertNothingWritten();
  }

  @Test
  void aCallerOfTheWrongTypeIsForbiddenAndNothingIsRead() {
    when(users.requireByTokenAndType(CARLOS_UID, UserType.CLIENT))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "user.type.forbidden"));

    assertRefused(
        () -> service.send(CARLOS_UID, request(item("lucas", mondayRoundTrip()))),
        HttpStatus.FORBIDDEN,
        "user.type.forbidden");
    verifyNoInteractions(drivers, dependents, linkService, requests);
  }

  @Test
  void theClientReadsTheRequestWithTheDependentTokensInOneQuery() {
    ServiceRequestModel stored = storedRequest(ServiceRequestStatus.OPEN);
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(stored));
    when(dependents.findTokensByIdIn(List.of(21L))).thenReturn(List.of(new TokenRow(21L, "lucas")));

    ServiceRequestClientResponseDTO response = service.findForClient(ANA_UID, "request");

    assertThat(response.items())
        .extracting(item -> item.dependentToken(), item -> item.dependentName())
        .containsExactly(tuple("lucas", "Lucas Souza"));
  }

  @Test
  void aTokenOfAnotherClientsRequestIsNotFound() {
    when(requests.findByTokenAndClientId("theirs", 10L)).thenReturn(Optional.empty());

    assertRefused(
        () -> service.findForClient(ANA_UID, "theirs"),
        HttpStatus.NOT_FOUND,
        "service_request.not_found");
  }

  @Test
  void theDriverReadsTheRestrictedView() {
    ServiceRequestModel stored = storedRequest(ServiceRequestStatus.OPEN);
    when(requests.findByTokenAndDriverId("request", 20L)).thenReturn(Optional.of(stored));
    when(dependents.findTokensByIdIn(List.of(21L))).thenReturn(List.of(new TokenRow(21L, "lucas")));

    ServiceRequestDriverResponseDTO response = service.findForDriver(CARLOS_UID, "request");

    assertThat(response.clientFirstName()).isEqualTo("Ana");
    assertThat(response.items())
        .extracting(item -> item.dependentToken(), item -> item.dependentFirstName())
        .containsExactly(tuple("lucas", "Lucas"));
  }

  @Test
  void aTokenOfAnotherDriversRequestIsNotFound() {
    when(requests.findByTokenAndDriverId("theirs", 20L)).thenReturn(Optional.empty());

    assertRefused(
        () -> service.findForDriver(CARLOS_UID, "theirs"),
        HttpStatus.NOT_FOUND,
        "service_request.not_found");
  }

  @Test
  void theClientListsAPageWithTheItemsAndTokensReadOncePerPage() {
    ServiceRequestModel first = storedRequest(ServiceRequestStatus.OPEN);
    ServiceRequestModel second = storedRequest(ServiceRequestStatus.EXPIRED);
    second.setId(61L);
    Pageable pageable = PageRequest.of(0, 20);
    when(requests.findPageByClientIdAndStatusIn(
            10L, EnumSet.allOf(ServiceRequestStatus.class), pageable))
        .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
    when(requests.findWithItemsByIdIn(List.of(60L, 61L))).thenReturn(List.of(second, first));
    when(dependents.findTokensByIdIn(List.of(21L))).thenReturn(List.of(new TokenRow(21L, "lucas")));

    Page<ServiceRequestClientResponseDTO> page = service.findPageForClient(ANA_UID, null, pageable);

    assertThat(page.getContent())
        .extracting(response -> response.status())
        .containsExactly(ServiceRequestStatus.OPEN, ServiceRequestStatus.EXPIRED);
    verify(dependents).findTokensByIdIn(List.of(21L));
  }

  @Test
  void theStatusFilterNarrowsTheListOfEachParty() {
    Pageable pageable = PageRequest.of(0, 20);
    when(requests.findPageByClientIdAndStatusIn(anyLong(), any(), eq(pageable)))
        .thenReturn(Page.empty(pageable));
    when(requests.findPageByDriverIdAndStatusIn(anyLong(), any(), eq(pageable)))
        .thenReturn(Page.empty(pageable));

    service.findPageForClient(ANA_UID, Set.of(ServiceRequestStatus.ANSWERED), pageable);
    service.findPageForDriver(
        CARLOS_UID, Set.of(ServiceRequestStatus.OPEN, ServiceRequestStatus.ANSWERED), pageable);

    verify(requests)
        .findPageByClientIdAndStatusIn(10L, Set.of(ServiceRequestStatus.ANSWERED), pageable);
    verify(requests)
        .findPageByDriverIdAndStatusIn(
            20L, Set.of(ServiceRequestStatus.OPEN, ServiceRequestStatus.ANSWERED), pageable);
    verify(requests, never()).findWithItemsByIdIn(any());
  }

  @Test
  void anEmptyStatusFilterListsEveryStatus() {
    Pageable pageable = PageRequest.of(0, 20);
    when(requests.findPageByClientIdAndStatusIn(anyLong(), any(), eq(pageable)))
        .thenReturn(Page.empty(pageable));

    service.findPageForClient(ANA_UID, Set.of(), pageable);

    verify(requests)
        .findPageByClientIdAndStatusIn(10L, EnumSet.allOf(ServiceRequestStatus.class), pageable);
  }

  @Test
  void withdrawingAnAnsweredRequestDeclinesItsSentProposal() {
    ServiceRequestModel answered = storedRequest(ServiceRequestStatus.ANSWERED);
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(answered));
    when(requests.updateStatus(
            60L, Set.of(ServiceRequestStatus.ANSWERED), ServiceRequestStatus.WITHDRAWN))
        .thenReturn(1);
    ProposalModel sent = new ProposalModel();
    sent.setId(70L);
    when(proposals.findByClientDriverIdAndStatus(40L, ProposalStatus.SENT))
        .thenReturn(Optional.of(sent));

    service.withdraw(ANA_UID, "request");

    verify(proposals).updateStatus(70L, ProposalStatus.SENT, ProposalStatus.DECLINED);
  }

  @Test
  void withdrawingAnOpenRequestTouchesNoProposal() {
    ServiceRequestModel open = storedRequest(ServiceRequestStatus.OPEN);
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(open));
    when(requests.updateStatus(
            60L, Set.of(ServiceRequestStatus.OPEN), ServiceRequestStatus.WITHDRAWN))
        .thenReturn(1);

    service.withdraw(ANA_UID, "request");

    verifyNoInteractions(proposals);
  }

  @Test
  void decliningAnAnsweredRequestWithdrawsItsSentProposalAndKeepsTheReason() {
    ServiceRequestModel answered = storedRequest(ServiceRequestStatus.ANSWERED);
    when(requests.findByTokenAndDriverId("request", 20L)).thenReturn(Optional.of(answered));
    when(requests.updateStatusWithDeclineReason(
            60L, Set.of(ServiceRequestStatus.ANSWERED), ServiceRequestStatus.DECLINED, "Sem vaga"))
        .thenReturn(1);
    ProposalModel sent = new ProposalModel();
    sent.setId(70L);
    when(proposals.findByClientDriverIdAndStatus(40L, ProposalStatus.SENT))
        .thenReturn(Optional.of(sent));

    service.decline(CARLOS_UID, "request", new ServiceRequestDeclineRequestDTO("  Sem vaga "));

    verify(proposals).updateStatus(70L, ProposalStatus.SENT, ProposalStatus.WITHDRAWN);
  }

  @Test
  void aDeclineWithoutReasonStoresNone() {
    ServiceRequestModel open = storedRequest(ServiceRequestStatus.OPEN);
    when(requests.findByTokenAndDriverId("request", 20L)).thenReturn(Optional.of(open));
    when(requests.updateStatusWithDeclineReason(any(), any(), any(), any())).thenReturn(1);

    service.decline(CARLOS_UID, "request", null);
    service.decline(CARLOS_UID, "request", new ServiceRequestDeclineRequestDTO("   "));

    verify(requests, times(2))
        .updateStatusWithDeclineReason(
            60L, Set.of(ServiceRequestStatus.OPEN), ServiceRequestStatus.DECLINED, null);
  }

  @Test
  void aRequestThatIsNotOpenCannotBeWithdrawnNorDeclined() {
    for (ServiceRequestStatus terminal :
        EnumSet.complementOf(EnumSet.copyOf(ServiceRequestStatus.OPEN_STATUSES))) {
      ServiceRequestModel closed = storedRequest(terminal);
      when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(closed));
      when(requests.findByTokenAndDriverId("request", 20L)).thenReturn(Optional.of(closed));

      assertRefused(
          () -> service.withdraw(ANA_UID, "request"),
          HttpStatus.CONFLICT,
          "service_request.not_open");
      assertRefused(
          () -> service.decline(CARLOS_UID, "request", null),
          HttpStatus.CONFLICT,
          "service_request.not_open");
    }
    verify(requests, never()).updateStatus(any(), any(), any());
    verify(requests, never()).updateStatusWithDeclineReason(any(), any(), any(), any());
  }

  @Test
  void anOpenRequestPastItsDeadlineIsNotOpenEvenBeforeTheSweep() {
    ServiceRequestModel overdue = storedRequest(ServiceRequestStatus.OPEN);
    overdue.setExpiresAt(Instant.now().minusSeconds(1));
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(overdue));

    assertRefused(
        () -> service.withdraw(ANA_UID, "request"),
        HttpStatus.CONFLICT,
        "service_request.not_open");
    verify(requests, never()).updateStatus(any(), any(), any());
  }

  @Test
  void losingTheConditionalWriteIsAConflictAndClosesNoProposal() {
    ServiceRequestModel answered = storedRequest(ServiceRequestStatus.ANSWERED);
    when(requests.findByTokenAndClientId("request", 10L)).thenReturn(Optional.of(answered));
    when(requests.updateStatus(any(), any(), any())).thenReturn(0);

    assertRefused(
        () -> service.withdraw(ANA_UID, "request"),
        HttpStatus.CONFLICT,
        "service_request.not_open");
    verifyNoInteractions(proposals);
  }

  private void assertNothingWritten() {
    verifyNoInteractions(linkService);
    verify(requests, never()).saveAndFlush(any());
  }

  private void assertRefused(Runnable call, HttpStatus status, String key) {
    assertThatThrownBy(() -> call.run())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(status);
              assertThat(e.getReason()).isEqualTo(key);
            });
  }

  private ServiceRequestCreateRequestDTO request(ServiceRequestItemRequestDTO... items) {
    return new ServiceRequestCreateRequestDTO(
        "carlos", DESIRED_START, " Portão azul ", List.of(items));
  }

  private ServiceRequestItemRequestDTO item(
      String dependentToken, List<ServiceRequestDayRequestDTO> days) {
    return new ServiceRequestItemRequestDTO(dependentToken, days);
  }

  private List<ServiceRequestDayRequestDTO> roundTrip(List<DayOfWeek> weekdays) {
    return weekdays.stream().map(weekday -> day(weekday, true, true)).toList();
  }

  private List<ServiceRequestDayRequestDTO> mondayRoundTrip() {
    return roundTrip(List.of(DayOfWeek.MONDAY));
  }

  private ServiceRequestDayRequestDTO day(
      DayOfWeek weekday, boolean needsOutbound, boolean needsReturn) {
    return new ServiceRequestDayRequestDTO(weekday, needsOutbound, needsReturn);
  }

  private ServiceRequestModel storedRequest(ServiceRequestStatus status) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(DayOfWeek.MONDAY);
    day.setSchoolShift(SchoolShift.MORNING);
    day.setNeedsOutbound(true);
    day.setNeedsReturn(true);
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setId(80L);
    item.setDependentId(21L);
    item.setSnapshot(
        new ServiceRequestItemModel.Snapshot(
            ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
            new ServiceRequestItemModel.Snapshot.Dependent("Lucas Souza", LUCAS_BIRTH_DATE),
            new ServiceRequestItemModel.Snapshot.School("Escola Municipal"),
            new ServiceRequestItemModel.Snapshot.Pickup("Campinas", "SP", "Cambuí")));
    item.addDay(day);
    ServiceRequestModel request = new ServiceRequestModel();
    request.setId(60L);
    request.setToken("request");
    request.setClientDriver(link);
    request.setStatus(status);
    request.setDesiredStartDate(DESIRED_START);
    request.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
    request.addItem(item);
    return request;
  }

  private UserModel user(Long id, String name, UserType type) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setName(name);
    user.setType(type);
    return user;
  }

  private DependentModel dependent(
      Long id, String token, String name, LocalDate birthDate, Long addressId) {
    DependentModel dependent = new DependentModel();
    dependent.setId(id);
    dependent.setToken(token);
    dependent.setClientId(10L);
    dependent.setName(name);
    dependent.setBirthDate(birthDate);
    dependent.setSchoolId(7L);
    dependent.setAddressId(addressId);
    return dependent;
  }

  private AddressModel address(Long id, String neighborhood) {
    StateModel state = new StateModel();
    state.setUf("SP");
    CityModel city = new CityModel();
    city.setId(30L);
    city.setName("Campinas");
    city.setState(state);
    AddressModel address = new AddressModel();
    address.setId(id);
    address.setCity(city);
    address.setStreet("Rua A");
    address.setNeighborhood(neighborhood);
    return address;
  }

  private List<DependentSchoolDayModel> grid(Long dependentId, SchoolShift... mondayOnwards) {
    List<DependentSchoolDayModel> days = new ArrayList<>();
    for (int index = 0; index < mondayOnwards.length; index++) {
      DependentSchoolDayModel day = new DependentSchoolDayModel();
      day.setDependentId(dependentId);
      day.setWeekday(DayOfWeek.MONDAY.plus(index));
      day.setSchoolShift(mondayOnwards[index]);
      days.add(day);
    }
    return days;
  }

  private record TokenRow(Long id, String token) implements DependentRepository.IdAndToken {
    @Override
    public Long getId() {
      return id;
    }

    @Override
    public String getToken() {
      return token;
    }
  }
}
