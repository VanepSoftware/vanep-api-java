package br.com.vanep.proposal.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.contract.enums.ContractTermsViolation;
import br.com.vanep.contract.service.ContractTermsPolicy;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.dto.ProposalAdjustmentRequestDTO;
import br.com.vanep.proposal.dto.ProposalCreateRequestDTO;
import br.com.vanep.proposal.dto.ProposalDeclineRequestDTO;
import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.proposal.dto.ProposalResponseDTO;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.proposal.event.ProposalEvent;
import br.com.vanep.proposal.mapper.ProposalMapper;
import br.com.vanep.proposal.model.ProposalAdjustmentModel;
import br.com.vanep.proposal.model.ProposalModel;
import br.com.vanep.proposal.repository.ProposalAdjustmentRepository;
import br.com.vanep.proposal.repository.ProposalRepository;
import br.com.vanep.schedule.service.ScheduleService;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.servicerequest.repository.ServiceRequestRepository;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProposalService {

  private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

  static final int DEFAULT_VALID_DAYS = 7;

  private final ProposalRepository proposals;
  private final ProposalAdjustmentRepository adjustments;
  private final ServiceRequestRepository requests;
  private final UserService users;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final AddressRepository addresses;
  private final VehicleRepository vehicles;
  private final ScheduleService schedules;
  private final ContractTermsPolicy termsPolicy;
  private final ProposalAcceptancePolicy acceptancePolicy;
  private final ProposalMapper mapper;
  private final TransactionTemplate transactions;
  private final ApplicationEventPublisher events;
  private final MessageSource messages;

  public ProposalService(
      ProposalRepository proposals,
      ProposalAdjustmentRepository adjustments,
      ServiceRequestRepository requests,
      UserService users,
      ClientRepository clients,
      DriverRepository drivers,
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      AddressRepository addresses,
      VehicleRepository vehicles,
      ScheduleService schedules,
      ContractTermsPolicy termsPolicy,
      ProposalAcceptancePolicy acceptancePolicy,
      ProposalMapper mapper,
      TransactionTemplate transactions,
      ApplicationEventPublisher events,
      MessageSource messages) {
    this.proposals = proposals;
    this.adjustments = adjustments;
    this.requests = requests;
    this.users = users;
    this.clients = clients;
    this.drivers = drivers;
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.addresses = addresses;
    this.vehicles = vehicles;
    this.schedules = schedules;
    this.termsPolicy = termsPolicy;
    this.acceptancePolicy = acceptancePolicy;
    this.mapper = mapper;
    this.transactions = transactions;
    this.events = events;
    this.messages = messages;
  }

  @Transactional
  public ProposalResponseDTO send(
      String callerUid, String requestToken, ProposalCreateRequestDTO body) {
    Instant now = Instant.now();
    DriverModel driver = requireCallerDriver(callerUid);
    ServiceRequestModel request = requireRequestOfDriver(driver, requestToken);
    ensureRequestTakesProposals(request, now);
    ensureValidPeriod(body);
    Map<String, Long> requestedIds = requireItemsCoveringTheRequest(request, body.items());
    VehicleModel vehicle = requireOwnActiveVehicle(driver, body.vehicleToken());
    // Read before the request row is locked, so a version sent meanwhile makes the writes miss.
    Optional<ProposalModel> current =
        proposals.findByClientDriverIdAndStatus(
            request.getClientDriver().getId(), ProposalStatus.SENT);
    Optional<ProposalModel> latest =
        proposals.findTopByServiceRequestIdOrderByVersionDesc(request.getId());

    ensureChanged(requests.answer(request.getId(), now), "proposal.request.not_open");
    current.ifPresent(
        sent ->
            ensureChanged(
                proposals.updateStatus(
                    sent.getId(), ProposalStatus.SENT, ProposalStatus.SUPERSEDED),
                "proposal.sent_conflict"));
    ProposalModel draft = newProposal(body, request, vehicle, latest, requestedIds, now);
    ProposalModel saved = keepingOneSentPerPair(() -> proposals.saveAndFlush(draft));
    events.publishEvent(
        new ProposalEvent.Sent(
            saved.getToken(), request.getClientDriver().getClient().getUser().getToken()));
    return toDriverResponse(requireOfDriver(driver, saved.getToken()));
  }

  @Transactional(readOnly = true)
  public ProposalResponseDTO findForClient(String callerUid, String token) {
    return toClientResponse(requireOfClient(requireCallerClient(callerUid), token));
  }

  @Transactional(readOnly = true)
  public ProposalResponseDTO findForDriver(String callerUid, String token) {
    return toDriverResponse(requireOfDriver(requireCallerDriver(callerUid), token));
  }

  @Transactional(readOnly = true)
  public List<ProposalResponseDTO> findChainForClient(String callerUid, String requestToken) {
    ClientModel client = requireCallerClient(callerUid);
    ServiceRequestModel request =
        requests
            .findByTokenAndClientId(requestToken, client.getId())
            .orElseThrow(() -> notFound("service_request.not_found"));
    List<ProposalModel> chain = proposals.findChainByServiceRequestId(request.getId());
    Related related = relatedTo(request, chain);
    return chain.stream()
        .map(
            proposal ->
                mapper.toClientResponse(
                    proposal, request, related.dependentTokens(), related.adjustmentsOf(proposal)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ProposalResponseDTO> findChainForDriver(String callerUid, String requestToken) {
    ServiceRequestModel request =
        requireRequestOfDriver(requireCallerDriver(callerUid), requestToken);
    List<ProposalModel> chain = proposals.findChainByServiceRequestId(request.getId());
    Related related = relatedTo(request, chain);
    return chain.stream()
        .map(
            proposal ->
                mapper.toDriverResponse(
                    proposal, request, related.dependentTokens(), related.adjustmentsOf(proposal)))
        .toList();
  }

  @Transactional
  public ProposalResponseDTO accept(String callerUid, String token) {
    Instant now = Instant.now();
    ClientModel client = requireCallerClient(callerUid);
    ProposalModel proposal = requireOfClient(client, token);
    ensureCurrent(proposal);
    if (!proposal.getValidUntil().isAfter(now)) {
      throw conflict("proposal.expired");
    }
    ensureStillHolds(proposal, requireRequestWithItems(proposal));

    ensureChanged(
        requests.updateStatus(
            proposal.getServiceRequest().getId(),
            Set.of(ServiceRequestStatus.ANSWERED),
            ServiceRequestStatus.CONVERTED),
        "proposal.not_current");
    ensureChanged(proposals.accept(proposal.getId(), now), "proposal.not_current");
    events.publishEvent(new ProposalEvent.Accepted(token, driverUidOf(proposal)));
    return toClientResponse(requireOfClient(client, token));
  }

  @Transactional
  public ProposalResponseDTO decline(
      String callerUid, String token, ProposalDeclineRequestDTO body) {
    ClientModel client = requireCallerClient(callerUid);
    ProposalModel proposal = requireOfClient(client, token);
    ensureCurrent(proposal);

    ensureChanged(
        requests.updateStatus(
            proposal.getServiceRequest().getId(),
            Set.of(ServiceRequestStatus.ANSWERED),
            ServiceRequestStatus.WITHDRAWN),
        "proposal.not_current");
    ensureChanged(proposals.decline(proposal.getId(), reasonOf(body)), "proposal.not_current");
    events.publishEvent(new ProposalEvent.Declined(token, driverUidOf(proposal)));
    return toClientResponse(requireOfClient(client, token));
  }

  @Transactional
  public ProposalResponseDTO requestAdjustment(
      String callerUid, String token, ProposalAdjustmentRequestDTO body) {
    ClientModel client = requireCallerClient(callerUid);
    if (!StringUtils.hasText(body.suggestedValue()) && !StringUtils.hasText(body.note())) {
      throw unprocessableContent("proposal.adjustment.empty");
    }
    ProposalModel proposal = requireOfClient(client, token);
    ensureCurrent(proposal);

    ensureChanged(proposals.touchSent(proposal.getId()), "proposal.not_current");
    adjustments.save(mapper.toAdjustmentModel(proposal.getId(), body));
    events.publishEvent(new ProposalEvent.AdjustmentRequested(token, driverUidOf(proposal)));
    return toClientResponse(requireOfClient(client, token));
  }

  @Transactional
  public ProposalResponseDTO withdraw(String callerUid, String token) {
    DriverModel driver = requireCallerDriver(callerUid);
    ProposalModel proposal = requireOfDriver(driver, token);
    ensureCurrent(proposal);
    closeAndReopenRequest(proposal, ProposalStatus.WITHDRAWN, Instant.now());
    return toDriverResponse(requireOfDriver(driver, token));
  }

  public int expireOverdue(Instant now, int limit) {
    int expired = 0;
    for (Long id : proposals.findOverdueSentIds(now, Limit.of(limit))) {
      if (expire(id, now)) {
        expired++;
      }
    }
    return expired;
  }

  // A failing row is logged and retried next round, so it never holds back the rows behind it.
  boolean expire(Long id, Instant now) {
    try {
      transactions.executeWithoutResult(
          status ->
              closeAndReopenRequest(
                  proposals.findById(id).orElseThrow(), ProposalStatus.EXPIRED, now));
      return true;
    } catch (ResponseStatusException changedMeanwhile) {
      log.debug("Proposal {} changed before it expired; skipped.", id);
      return false;
    } catch (RuntimeException unexpected) {
      log.warn("Proposal {} failed to expire; retried next round.", id, unexpected);
      return false;
    }
  }

  void closeAndReopenRequest(ProposalModel proposal, ProposalStatus closedAs, Instant now) {
    ensureChanged(
        requests.reopen(
            proposal.getServiceRequest().getId(), now.plus(ServiceRequestService.ANSWER_WINDOW)),
        "proposal.not_current");
    ensureChanged(
        proposals.updateStatus(proposal.getId(), ProposalStatus.SENT, closedAs),
        "proposal.not_current");
  }

  void ensureRequestTakesProposals(ServiceRequestModel request, Instant now) {
    boolean open = ServiceRequestStatus.OPEN_STATUSES.contains(request.getStatus());
    boolean overdue =
        request.getStatus() == ServiceRequestStatus.OPEN && !request.getExpiresAt().isAfter(now);
    if (!open || overdue) {
      throw conflict("proposal.request.not_open");
    }
  }

  void ensureValidPeriod(ProposalCreateRequestDTO body) {
    termsPolicy
        .validate(body.startsOn(), body.endsOn())
        .ifPresent(
            violation -> {
              throw unprocessableContent(messageKey(violation));
            });
  }

  String messageKey(ContractTermsViolation violation) {
    return switch (violation) {
      case PERIOD_INVALID -> "proposal.period.invalid";
      case PERIOD_TOO_LONG -> "proposal.period.too_long";
    };
  }

  // A dependent removed since the request still counts for the match; it is refused on its own.
  Map<String, Long> requireItemsCoveringTheRequest(
      ServiceRequestModel request, List<ProposalItemRequestDTO> items) {
    List<Long> requestedIds =
        request.getItems().stream().map(item -> item.getDependentId()).toList();
    Map<String, Long> idsByToken =
        dependents.findTokensByIdIn(requestedIds).stream()
            .collect(Collectors.toMap(row -> row.getToken(), row -> row.getId()));
    List<String> offered = items.stream().map(item -> item.dependentToken()).toList();
    boolean sameDependents =
        offered.size() == idsByToken.size() && Set.copyOf(offered).equals(idsByToken.keySet());
    if (!sameDependents) {
      throw unprocessableContent("proposal.items.mismatch");
    }
    if (dependents.findAllById(requestedIds).size() < requestedIds.size()) {
      throw unprocessableContent("proposal.item.dependent_unavailable");
    }
    return idsByToken;
  }

  VehicleModel requireOwnActiveVehicle(DriverModel driver, String vehicleToken) {
    return vehicles
        .findByToken(vehicleToken)
        .filter(vehicle -> vehicle.isActive())
        .filter(vehicle -> vehicle.getDriver().getId().equals(driver.getId()))
        .orElseThrow(() -> notFound("proposal.vehicle.not_found"));
  }

  ProposalModel newProposal(
      ProposalCreateRequestDTO body,
      ServiceRequestModel request,
      VehicleModel vehicle,
      Optional<ProposalModel> latest,
      Map<String, Long> requestedIds,
      Instant now) {
    ProposalModel proposal = new ProposalModel();
    proposal.setClientDriver(request.getClientDriver());
    proposal.setServiceRequest(request);
    proposal.setVersion(latest.map(previous -> previous.getVersion() + 1).orElse(1));
    proposal.setParentProposalId(latest.map(previous -> previous.getId()).orElse(null));
    proposal.setStatus(ProposalStatus.SENT);
    int validDays = body.validDays() == null ? DEFAULT_VALID_DAYS : body.validDays();
    proposal.setValidUntil(now.plus(Duration.ofDays(validDays)));
    proposal.setStartsOn(body.startsOn());
    proposal.setEndsOn(body.endsOn());
    proposal.setTotalAmount(body.totalAmount());
    proposal.setInstallments(body.installments());
    proposal.setDueDay(body.dueDay());
    proposal.setEnrollmentFee(body.enrollmentFee());
    proposal.setAdjustmentPolicy(body.adjustmentPolicy());
    proposal.setCancellationPolicy(body.cancellationPolicy());
    proposal.setVehicleId(vehicle.getId());
    proposal.setSnapshot(mapper.toSnapshot(vehicle));
    proposal.setMessage(StringUtils.hasText(body.message()) ? body.message().strip() : null);
    for (ProposalItemRequestDTO item : body.items()) {
      proposal.addItem(
          mapper.toItemModel(
              requestedIds.get(item.dependentToken()),
              item.monthlyAmount(),
              schedules.create(item.slots())));
    }
    return proposal;
  }

  // The pair's SENT index and the request's version index decide the race between two versions
  // sent at once, which the reads above cannot see.
  <T> T keepingOneSentPerPair(Supplier<T> write) {
    try {
      return write.get();
    } catch (DataIntegrityViolationException concurrentVersion) {
      throw conflict("proposal.sent_conflict");
    }
  }

  void ensureStillHolds(ProposalModel proposal, ServiceRequestModel request) {
    List<Long> dependentIds =
        request.getItems().stream().map(item -> item.getDependentId()).toList();
    List<DependentModel> current = dependents.lockByIdIn(dependentIds);
    VehicleModel vehicle = vehicles.lockById(proposal.getVehicleId()).orElse(null);
    Map<Long, AddressModel> pickups =
        indexBy(
            addresses.findAllById(
                current.stream().map(dependent -> dependent.getAddressId()).toList()),
            address -> address.getId());
    boolean changed =
        !acceptancePolicy
            .findChanges(
                request,
                proposal,
                current,
                pickups,
                schoolDays.findByDependentIdIn(dependentIds),
                vehicle,
                proposal.getClientDriver().getDriver())
            .isEmpty();
    if (changed) {
      throw conflict("proposal.accept.stale");
    }
  }

  void ensureCurrent(ProposalModel proposal) {
    if (proposal.getStatus() != ProposalStatus.SENT) {
      throw conflict("proposal.not_current");
    }
  }

  void ensureChanged(int changedRows, String key) {
    if (changedRows == 0) {
      throw conflict(key);
    }
  }

  String driverUidOf(ProposalModel proposal) {
    return proposal.getClientDriver().getDriver().getUser().getToken();
  }

  String reasonOf(ProposalDeclineRequestDTO body) {
    return body == null || !StringUtils.hasText(body.reason()) ? null : body.reason().strip();
  }

  ProposalResponseDTO toClientResponse(ProposalModel proposal) {
    ServiceRequestModel request = requireRequestWithItems(proposal);
    Related related = relatedTo(request, List.of(proposal));
    return mapper.toClientResponse(
        proposal, request, related.dependentTokens(), related.adjustmentsOf(proposal));
  }

  ProposalResponseDTO toDriverResponse(ProposalModel proposal) {
    ServiceRequestModel request = requireRequestWithItems(proposal);
    Related related = relatedTo(request, List.of(proposal));
    return mapper.toDriverResponse(
        proposal, request, related.dependentTokens(), related.adjustmentsOf(proposal));
  }

  Related relatedTo(ServiceRequestModel request, List<ProposalModel> loaded) {
    List<Long> dependentIds =
        request.getItems().stream().map(item -> item.getDependentId()).toList();
    Map<Long, String> tokens =
        dependents.findTokensByIdIn(dependentIds).stream()
            .collect(Collectors.toMap(row -> row.getId(), row -> row.getToken()));
    Map<Long, List<ProposalAdjustmentModel>> adjustmentsByProposal =
        loaded.isEmpty()
            ? Map.of()
            : adjustments
                .findByProposalIdInOrderByCreatedAtAscIdAsc(
                    loaded.stream().map(proposal -> proposal.getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(adjustment -> adjustment.getProposalId()));
    return new Related(tokens, adjustmentsByProposal);
  }

  ServiceRequestModel requireRequestWithItems(ProposalModel proposal) {
    return requests.findWithItemsByIdIn(List.of(proposal.getServiceRequest().getId())).stream()
        .findFirst()
        .orElseThrow(() -> notFound("service_request.not_found"));
  }

  <K, V> Map<K, V> indexBy(Collection<V> values, Function<V, K> key) {
    return values.stream().collect(Collectors.toMap(key, value -> value));
  }

  ClientModel requireCallerClient(String callerUid) {
    UserModel caller = users.requireByTokenAndType(callerUid, UserType.CLIENT);
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

  ServiceRequestModel requireRequestOfDriver(DriverModel driver, String requestToken) {
    return requests
        .findByTokenAndDriverId(requestToken, driver.getId())
        .orElseThrow(() -> notFound("service_request.not_found"));
  }

  ProposalModel requireOfClient(ClientModel client, String token) {
    return proposals
        .findByTokenAndClientId(token, client.getId())
        .orElseThrow(() -> notFound("proposal.not_found"));
  }

  ProposalModel requireOfDriver(DriverModel driver, String token) {
    return proposals
        .findByTokenAndDriverId(token, driver.getId())
        .orElseThrow(() -> notFound("proposal.not_found"));
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

  record Related(
      Map<Long, String> dependentTokens, Map<Long, List<ProposalAdjustmentModel>> adjustments) {
    List<ProposalAdjustmentModel> adjustmentsOf(ProposalModel proposal) {
      return adjustments.getOrDefault(proposal.getId(), List.of());
    }
  }
}
