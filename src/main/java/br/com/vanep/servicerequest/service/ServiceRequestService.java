package br.com.vanep.servicerequest.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
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
import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.mapper.ProposalMapper;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestCreateRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDeclineRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestItemsViolation;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.mapper.ServiceRequestMapper;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceRequestService {

  public static final Duration ANSWER_WINDOW = Duration.ofHours(72);
  static final int OPEN_LIMIT_PER_CLIENT = 5;

  private static final Set<ServiceRequestStatus> WITH_CURRENT_PROPOSAL =
      Set.of(ServiceRequestStatus.ANSWERED, ServiceRequestStatus.CONVERTED);

  private final ServiceRequestRepository requests;
  private final ProposalRepository proposals;
  private final ClientDriverService linkService;
  private final ContractRepository contracts;
  private final ContractReadinessService readiness;
  private final UserService users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final SchoolRepository schools;
  private final AddressRepository addresses;
  private final ServiceRequestItemsPolicy itemsPolicy;
  private final ServiceRequestTransitionPolicy transitionPolicy;
  private final ServiceRequestMapper mapper;
  private final ProposalMapper proposalMapper;
  private final MessageSource messages;

  public ServiceRequestService(
      ServiceRequestRepository requests,
      ProposalRepository proposals,
      ClientDriverService linkService,
      ContractRepository contracts,
      ContractReadinessService readiness,
      UserService users,
      ClientRepository clients,
      DriverRepository drivers,
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      SchoolRepository schools,
      AddressRepository addresses,
      ServiceRequestItemsPolicy itemsPolicy,
      ServiceRequestTransitionPolicy transitionPolicy,
      ServiceRequestMapper mapper,
      ProposalMapper proposalMapper,
      MessageSource messages) {
    this.requests = requests;
    this.proposals = proposals;
    this.linkService = linkService;
    this.contracts = contracts;
    this.readiness = readiness;
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.schools = schools;
    this.addresses = addresses;
    this.itemsPolicy = itemsPolicy;
    this.transitionPolicy = transitionPolicy;
    this.mapper = mapper;
    this.proposalMapper = proposalMapper;
    this.messages = messages;
  }

  @Transactional
  public ServiceRequestClientResponseDTO send(
      String callerUid, ServiceRequestCreateRequestDTO request) {
    UserModel caller = users.requireByTokenAndType(callerUid, UserType.CLIENT);
    ensureValidItems(request.items());
    ClientModel client = requireClientOf(caller);
    DriverModel driver = requireDriverTakingRequests(request.driverToken());
    List<DependentModel> chosen = lockOwnDependents(client, request.items());
    List<DependentSchoolDayModel> grids = schoolDays.findByDependentIdIn(idsOf(chosen));
    ensureReady(caller, chosen, grids);
    ServiceRequestModel draft = newRequest(request, chosen, grids);

    ClientDriverModel link =
        keepingOneOpenRequestPerPair(() -> linkService.findOrCreateForRequest(client, driver));
    ensureLinkTakesRequests(link);
    ensureNoOpenRequest(link);
    ensureBelowOpenLimit(client);
    draft.setClientDriver(link);
    ServiceRequestModel saved = keepingOneOpenRequestPerPair(() -> requests.saveAndFlush(draft));
    return mapper.toClientResponse(saved, tokensById(chosen), null);
  }

  @Transactional(readOnly = true)
  public ServiceRequestClientResponseDTO findForClient(String callerUid, String token) {
    ClientModel client = requireCallerClient(callerUid);
    return toClientResponse(requireOfClient(client, token));
  }

  @Transactional(readOnly = true)
  public Page<ServiceRequestClientResponseDTO> findPageForClient(
      String callerUid, Set<ServiceRequestStatus> statuses, Pageable pageable) {
    ClientModel client = requireCallerClient(callerUid);
    Page<ServiceRequestModel> page =
        requests.findPageByClientIdAndStatusIn(
            client.getId(), statusesMatching(statuses), pageable);
    Map<Long, ServiceRequestModel> withItems = withItems(page.getContent());
    Map<Long, String> tokens = dependentTokensOf(withItems.values());
    Map<Long, ProposalSummaryResponseDTO> current = currentProposalsOf(withItems.values());
    return page.map(
        request ->
            mapper.toClientResponse(
                withItems.get(request.getId()), tokens, current.get(request.getId())));
  }

  @Transactional(readOnly = true)
  public ServiceRequestDriverResponseDTO findForDriver(String callerUid, String token) {
    DriverModel driver = requireCallerDriver(callerUid);
    return toDriverResponse(requireOfDriver(driver, token));
  }

  @Transactional(readOnly = true)
  public Page<ServiceRequestDriverResponseDTO> findPageForDriver(
      String callerUid, Set<ServiceRequestStatus> statuses, Pageable pageable) {
    DriverModel driver = requireCallerDriver(callerUid);
    Page<ServiceRequestModel> page =
        requests.findPageByDriverIdAndStatusIn(
            driver.getId(), statusesMatching(statuses), pageable);
    Map<Long, ServiceRequestModel> withItems = withItems(page.getContent());
    Map<Long, String> tokens = dependentTokensOf(withItems.values());
    Map<Long, ProposalSummaryResponseDTO> current = currentProposalsOf(withItems.values());
    LocalDate today = today();
    return page.map(
        request ->
            mapper.toDriverResponse(
                withItems.get(request.getId()), tokens, today, current.get(request.getId())));
  }

  @Transactional
  public ServiceRequestClientResponseDTO withdraw(String callerUid, String token) {
    ClientModel client = requireCallerClient(callerUid);
    ServiceRequestModel request = requireOfClient(client, token);
    ServiceRequestStatus from = requireOpenFor(request, ServiceRequestStatus.WITHDRAWN);
    ensureChanged(
        requests.updateStatus(request.getId(), Set.of(from), ServiceRequestStatus.WITHDRAWN));
    closeSentProposalIfAnswered(from, request, ProposalStatus.DECLINED);
    return toClientResponse(requireOfClient(client, token));
  }

  @Transactional
  public ServiceRequestDriverResponseDTO decline(
      String callerUid, String token, ServiceRequestDeclineRequestDTO body) {
    DriverModel driver = requireCallerDriver(callerUid);
    ServiceRequestModel request = requireOfDriver(driver, token);
    ServiceRequestStatus from = requireOpenFor(request, ServiceRequestStatus.DECLINED);
    ensureChanged(
        requests.updateStatusWithDeclineReason(
            request.getId(), Set.of(from), ServiceRequestStatus.DECLINED, reasonOf(body)));
    closeSentProposalIfAnswered(from, request, ProposalStatus.WITHDRAWN);
    return toDriverResponse(requireOfDriver(driver, token));
  }

  void ensureValidItems(List<ServiceRequestItemRequestDTO> items) {
    itemsPolicy
        .validate(items)
        .ifPresent(
            violation -> {
              throw badRequest(messageKey(violation));
            });
  }

  String messageKey(ServiceRequestItemsViolation violation) {
    return switch (violation) {
      case DEPENDENT_DUPLICATE -> "service_request.item.dependent_duplicate";
      case WEEKDAY_DUPLICATE -> "service_request.day.weekday_duplicate";
      case DAY_WITHOUT_LEG -> "service_request.day.leg_required";
    };
  }

  DriverModel requireDriverTakingRequests(String driverToken) {
    DriverModel driver =
        drivers
            .findSearchableByToken(driverToken)
            .orElseThrow(() -> notFound("service_request.driver.not_found"));
    if (!driver.isAvailable()) {
      throw unprocessableContent("service_request.driver.not_available");
    }
    return driver;
  }

  List<DependentModel> lockOwnDependents(
      ClientModel client, List<ServiceRequestItemRequestDTO> items) {
    List<String> tokens = items.stream().map(item -> item.dependentToken()).toList();
    List<DependentModel> locked = dependents.lockByTokenInAndClientId(tokens, client.getId());
    if (locked.size() < tokens.size()) {
      throw notFound("service_request.dependent.not_found");
    }
    return locked;
  }

  void ensureReady(
      UserModel caller, List<DependentModel> chosen, List<DependentSchoolDayModel> grids) {
    if (!readiness.isReady(caller, chosen, grids)) {
      throw unprocessableContent("service_request.not_ready");
    }
  }

  ServiceRequestModel newRequest(
      ServiceRequestCreateRequestDTO body,
      List<DependentModel> chosen,
      List<DependentSchoolDayModel> grids) {
    Map<String, DependentModel> byToken = indexBy(chosen, dependent -> dependent.getToken());
    Map<Long, SchoolModel> schoolsById =
        indexBy(
            schools.findAllById(chosen.stream().map(dependent -> dependent.getSchoolId()).toList()),
            school -> school.getId());
    Map<Long, AddressModel> addressesById =
        indexBy(
            addresses.findAllById(
                chosen.stream().map(dependent -> dependent.getAddressId()).toList()),
            address -> address.getId());
    Map<Long, Map<DayOfWeek, SchoolShift>> gridsByDependent =
        grids.stream()
            .collect(
                Collectors.groupingBy(
                    day -> day.getDependentId(),
                    Collectors.toMap(day -> day.getWeekday(), day -> day.getSchoolShift())));

    ServiceRequestModel draft = new ServiceRequestModel();
    draft.setStatus(ServiceRequestStatus.OPEN);
    draft.setDesiredStartDate(body.desiredStartDate());
    draft.setNotes(StringUtils.hasText(body.notes()) ? body.notes().strip() : null);
    draft.setExpiresAt(Instant.now().plus(ANSWER_WINDOW));
    for (ServiceRequestItemRequestDTO itemRequest : body.items()) {
      DependentModel dependent = byToken.get(itemRequest.dependentToken());
      draft.addItem(
          newItem(
              itemRequest,
              dependent,
              schoolsById.get(dependent.getSchoolId()),
              addressesById.get(dependent.getAddressId()),
              gridsByDependent.getOrDefault(dependent.getId(), Map.of())));
    }
    return draft;
  }

  ServiceRequestItemModel newItem(
      ServiceRequestItemRequestDTO itemRequest,
      DependentModel dependent,
      SchoolModel school,
      AddressModel pickup,
      Map<DayOfWeek, SchoolShift> grid) {
    if (school == null || pickup == null) {
      throw unprocessableContent("service_request.not_ready");
    }
    ServiceRequestItemModel item = mapper.toItemModel(dependent, school, pickup);
    for (ServiceRequestDayRequestDTO day : itemRequest.days()) {
      SchoolShift schoolShift = grid.get(day.weekday());
      if (schoolShift == null) {
        throw unprocessableContent("service_request.day.no_school");
      }
      item.addDay(mapper.toDayModel(day, schoolShift));
    }
    return item;
  }

  void ensureLinkTakesRequests(ClientDriverModel link) {
    if (link.getStatus() == RelationshipStatus.BLOCKED) {
      throw unprocessableContent("service_request.link.blocked");
    }
    boolean hasContract =
        contracts.findStatusesByClientDriverId(link.getId()).stream()
            .anyMatch(status -> ContractStatus.SIGNED_AND_NOT_ENDED.contains(status));
    if (hasContract) {
      throw unprocessableContent("service_request.link.has_contract");
    }
  }

  void ensureNoOpenRequest(ClientDriverModel link) {
    if (requests
        .findByClientDriverIdAndStatusIn(link.getId(), ServiceRequestStatus.OPEN_STATUSES)
        .isPresent()) {
      throw conflict("service_request.open_exists");
    }
  }

  void ensureBelowOpenLimit(ClientModel client) {
    long open =
        requests.countByClientIdAndStatusIn(client.getId(), ServiceRequestStatus.OPEN_STATUSES);
    if (open >= OPEN_LIMIT_PER_CLIENT) {
      throw unprocessableContent("service_request.open_limit");
    }
  }

  // The pair's open-request index (and, for a first request, the pair's link index) decides the
  // race that the checks above cannot see.
  <T> T keepingOneOpenRequestPerPair(Supplier<T> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException concurrentRequest) {
      throw conflict("service_request.open_exists");
    }
  }

  // An OPEN request past its deadline is already expired for the parties, even before the sweep.
  ServiceRequestStatus requireOpenFor(ServiceRequestModel request, ServiceRequestStatus target) {
    boolean overdue =
        request.getStatus() == ServiceRequestStatus.OPEN
            && !request.getExpiresAt().isAfter(Instant.now());
    if (!transitionPolicy.allows(request.getStatus(), target) || overdue) {
      throw conflict("service_request.not_open");
    }
    return request.getStatus();
  }

  void ensureChanged(int changedRows) {
    if (changedRows == 0) {
      throw conflict("service_request.not_open");
    }
  }

  void closeSentProposalIfAnswered(
      ServiceRequestStatus from, ServiceRequestModel request, ProposalStatus closedAs) {
    if (from != ServiceRequestStatus.ANSWERED) {
      return;
    }
    proposals
        .findByClientDriverIdAndStatus(request.getClientDriver().getId(), ProposalStatus.SENT)
        .ifPresent(
            proposal -> proposals.updateStatus(proposal.getId(), ProposalStatus.SENT, closedAs));
  }

  String reasonOf(ServiceRequestDeclineRequestDTO body) {
    return body == null || !StringUtils.hasText(body.reason()) ? null : body.reason().strip();
  }

  Set<ServiceRequestStatus> statusesMatching(Set<ServiceRequestStatus> statuses) {
    return statuses == null || statuses.isEmpty()
        ? EnumSet.allOf(ServiceRequestStatus.class)
        : Set.copyOf(statuses);
  }

  Map<Long, ServiceRequestModel> withItems(List<ServiceRequestModel> page) {
    if (page.isEmpty()) {
      return Map.of();
    }
    return indexBy(
        requests.findWithItemsByIdIn(page.stream().map(request -> request.getId()).toList()),
        request -> request.getId());
  }

  Map<Long, String> dependentTokensOf(Collection<ServiceRequestModel> loaded) {
    List<Long> dependentIds =
        loaded.stream()
            .flatMap(request -> request.getItems().stream())
            .map(item -> item.getDependentId())
            .distinct()
            .toList();
    if (dependentIds.isEmpty()) {
      return Map.of();
    }
    return dependents.findTokensByIdIn(dependentIds).stream()
        .collect(Collectors.toMap(row -> row.getId(), row -> row.getToken()));
  }

  Map<Long, String> tokensById(List<DependentModel> chosen) {
    return chosen.stream()
        .collect(
            Collectors.toMap(dependent -> dependent.getId(), dependent -> dependent.getToken()));
  }

  List<Long> idsOf(List<DependentModel> chosen) {
    return chosen.stream().map(dependent -> dependent.getId()).toList();
  }

  <K, V> Map<K, V> indexBy(Collection<V> values, Function<V, K> key) {
    return values.stream().collect(Collectors.toMap(key, value -> value));
  }

  ServiceRequestClientResponseDTO toClientResponse(ServiceRequestModel request) {
    return mapper.toClientResponse(
        request,
        dependentTokensOf(List.of(request)),
        currentProposalsOf(List.of(request)).get(request.getId()));
  }

  ServiceRequestDriverResponseDTO toDriverResponse(ServiceRequestModel request) {
    return mapper.toDriverResponse(
        request,
        dependentTokensOf(List.of(request)),
        today(),
        currentProposalsOf(List.of(request)).get(request.getId()));
  }

  Map<Long, ProposalSummaryResponseDTO> currentProposalsOf(Collection<ServiceRequestModel> loaded) {
    List<Long> ids =
        loaded.stream()
            .filter(request -> WITH_CURRENT_PROPOSAL.contains(request.getStatus()))
            .map(request -> request.getId())
            .toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return proposals.findCurrentByServiceRequestIdIn(ids).stream()
        .collect(
            Collectors.toMap(
                row -> row.getServiceRequestId(), row -> proposalMapper.toSummaryResponse(row)));
  }

  LocalDate today() {
    return LocalDate.now(TripService.SERVICE_ZONE);
  }

  ClientModel requireCallerClient(String callerUid) {
    return requireClientOf(users.requireByTokenAndType(callerUid, UserType.CLIENT));
  }

  ClientModel requireClientOf(UserModel caller) {
    return clients
        .findByUserId(caller.getId())
        .orElseThrow(() -> notFound("client.profile.not_found"));
  }

  DriverModel requireCallerDriver(String callerUid) {
    UserModel caller = users.requireByTokenAndType(callerUid, UserType.DRIVER);
    return drivers
        .findByUserId(caller.getId())
        .orElseThrow(() -> notFound("user.driver_profile.not_found"));
  }

  ServiceRequestModel requireOfClient(ClientModel client, String token) {
    return requests
        .findByTokenAndClientId(token, client.getId())
        .orElseThrow(() -> notFound("service_request.not_found"));
  }

  ServiceRequestModel requireOfDriver(DriverModel driver, String token) {
    return requests
        .findByTokenAndDriverId(token, driver.getId())
        .orElseThrow(() -> notFound("service_request.not_found"));
  }

  ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  ResponseStatusException unprocessableContent(String key) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
