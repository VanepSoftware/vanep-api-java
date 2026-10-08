package br.com.vanep.stopchange.service;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.address.service.AddressPlaceResolverService;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.stopchange.dto.StopChangeCreateRequestDTO;
import br.com.vanep.stopchange.enums.StopChangeStatus;
import br.com.vanep.stopchange.event.StopChangeApprovedEvent;
import br.com.vanep.stopchange.event.StopChangeRejectedEvent;
import br.com.vanep.stopchange.event.StopChangeRequestedEvent;
import br.com.vanep.stopchange.model.StopChangeRequestModel;
import br.com.vanep.stopchange.repository.StopChangeRequestRepository;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StopChangeRequestService {

  private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

  private final StopChangeRequestRepository repository;
  private final DependentRepository dependentRepository;
  private final TripRepository tripRepository;
  private final ClientRepository clientRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final AddressRepository addressRepository;
  private final AddressPlaceResolverService placeResolver;
  private final StopChangeTransitionPolicy transitionPolicy;
  private final ApplicationEventPublisher eventPublisher;
  private final MessageSource messages;

  public StopChangeRequestService(
      StopChangeRequestRepository repository,
      DependentRepository dependentRepository,
      TripRepository tripRepository,
      ClientRepository clientRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      AddressRepository addressRepository,
      AddressPlaceResolverService placeResolver,
      StopChangeTransitionPolicy transitionPolicy,
      ApplicationEventPublisher eventPublisher,
      MessageSource messages) {
    this.repository = repository;
    this.dependentRepository = dependentRepository;
    this.tripRepository = tripRepository;
    this.clientRepository = clientRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.addressRepository = addressRepository;
    this.placeResolver = placeResolver;
    this.transitionPolicy = transitionPolicy;
    this.eventPublisher = eventPublisher;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public StopChangeRequestModel requestChange(
      String callerUid, StopChangeCreateRequestDTO request) {
    UserModel clientUser = requireUser(callerUid);
    ClientModel client =
        clientRepository
            .findByUserId(clientUser.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, message("stop_change.not_owner")));

    DependentModel dependent =
        dependentRepository
            .findByToken(request.dependentToken())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("dependent.not_found")));

    if (!dependent.getClientId().equals(client.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, message("stop_change.dependent_not_owned"));
    }

    TripModel trip =
        tripRepository
            .findByToken(request.tripToken())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("trip.not_found")));

    if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.trip_not_active"));
    }

    if (repository
        .findByDependentIdAndTripIdAndStatus(
            dependent.getId(), trip.getId(), StopChangeStatus.PENDING)
        .isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.already_pending"));
    }

    AddressModel dropoffAddress = resolveDropoffAddress(request);

    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setDependent(dependent);
    req.setTrip(trip);
    req.setServiceDate(trip.getServiceDate());
    req.setRequestedByUser(clientUser);
    req.setNewDropoffAddress(dropoffAddress);
    req.setReason(request.reason());
    req.setStatus(StopChangeStatus.PENDING);

    StopChangeRequestModel saved;
    try {
      saved = repository.save(req);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.already_pending"), ex);
    }

    eventPublisher.publishEvent(
        new StopChangeRequestedEvent(
            saved.getToken(), dependent.getId(), trip.getId(), clientUser.getId()));

    return saved;
  }

  @Transactional
  public StopChangeRequestModel approveChange(String callerUid, String requestToken) {
    UserModel driverUser = requireUser(callerUid);
    DriverModel driver =
        driverRepository
            .findByUserId(driverUser.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, message("driver.not_approved")));

    if (driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("driver.not_approved"));
    }

    StopChangeRequestModel req = requireRequest(requestToken);

    if (!req.getTrip().getDriver().getId().equals(driver.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, message("stop_change.driver_not_assigned"));
    }

    if (req.getTrip().getStatus() == TripStatus.COMPLETED
        || req.getTrip().getStatus() == TripStatus.CANCELLED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.trip_not_active"));
    }

    if (req.getStatus() == StopChangeStatus.APPROVED) {
      return req;
    }

    if (!transitionPolicy.canApprove(req.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.already_resolved"));
    }

    req.setStatus(StopChangeStatus.APPROVED);
    req.setRespondedByUser(driverUser);
    req.setRespondedAt(Instant.now());

    StopChangeRequestModel saved = repository.save(req);

    eventPublisher.publishEvent(
        new StopChangeApprovedEvent(
            saved.getToken(),
            saved.getDependent().getId(),
            saved.getTrip().getId(),
            driverUser.getId()));

    return saved;
  }

  @Transactional
  public StopChangeRequestModel rejectChange(String callerUid, String requestToken) {
    UserModel driverUser = requireUser(callerUid);
    DriverModel driver =
        driverRepository
            .findByUserId(driverUser.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, message("driver.not_approved")));

    if (driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("driver.not_approved"));
    }

    StopChangeRequestModel req = requireRequest(requestToken);

    if (!req.getTrip().getDriver().getId().equals(driver.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, message("stop_change.driver_not_assigned"));
    }

    if (req.getStatus() == StopChangeStatus.REJECTED) {
      return req;
    }

    if (!transitionPolicy.canReject(req.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("stop_change.already_resolved"));
    }

    req.setStatus(StopChangeStatus.REJECTED);
    req.setRespondedByUser(driverUser);
    req.setRespondedAt(Instant.now());

    StopChangeRequestModel saved = repository.save(req);

    eventPublisher.publishEvent(
        new StopChangeRejectedEvent(
            saved.getToken(),
            saved.getDependent().getId(),
            saved.getTrip().getId(),
            driverUser.getId()));

    return saved;
  }

  @Transactional
  public StopChangeRequestModel cancelChange(String callerUid, String requestToken) {
    UserModel clientUser = requireUser(callerUid);
    StopChangeRequestModel req = requireRequest(requestToken);

    if (!req.getRequestedByUser().getId().equals(clientUser.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("stop_change.not_owner"));
    }

    if (req.getStatus() == StopChangeStatus.CANCELLED) {
      return req;
    }

    if (!transitionPolicy.canCancel(req.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("stop_change.cannot_cancel"));
    }

    req.setStatus(StopChangeStatus.CANCELLED);
    return repository.save(req);
  }

  @Transactional(readOnly = true)
  public StopChangeRequestModel findByToken(String token) {
    return requireRequest(token);
  }

  @Transactional(readOnly = true)
  public List<StopChangeRequestModel> listTodayForClient(String callerUid) {
    UserModel user = requireUser(callerUid);
    LocalDate today = LocalDate.now(SAO_PAULO_ZONE);
    return repository.findByRequestedByUserIdAndServiceDate(user.getId(), today);
  }

  @Transactional(readOnly = true)
  public List<StopChangeRequestModel> listTodayForDriver(String callerUid) {
    UserModel user = requireUser(callerUid);
    DriverModel driver =
        driverRepository
            .findByUserId(user.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.FORBIDDEN, message("driver.not_approved")));
    LocalDate today = LocalDate.now(SAO_PAULO_ZONE);
    return repository.findByDriverIdAndServiceDate(driver.getId(), today);
  }

  @Transactional(readOnly = true)
  public Page<StopChangeRequestModel> findPage(Pageable pageable) {
    return repository.findPage(pageable);
  }

  @Transactional
  public void deleteByToken(String token) {
    StopChangeRequestModel model = requireRequest(token);
    repository.delete(model);
  }

  @Transactional
  public StopChangeRequestModel restoreByToken(String token) {
    if (!repository.existsDeletedByToken(token)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("stop_change.not_found"));
    }
    repository.restoreByToken(token);
    return requireRequest(token);
  }

  private AddressModel resolveDropoffAddress(StopChangeCreateRequestDTO request) {
    if (request.addressToken() != null && !request.addressToken().isBlank()) {
      return addressRepository
          .findByToken(request.addressToken())
          .orElseThrow(
              () ->
                  new ResponseStatusException(HttpStatus.NOT_FOUND, message("address.not_found")));
    }

    if (request.placeId() == null || request.placeId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("address.place_required"));
    }

    AddressModel address = new AddressModel();
    placeResolver.applyPlace(
        address, request.placeId(), request.sessionToken(), request.number(), request.complement());
    return addressRepository.save(address);
  }

  private UserModel requireUser(String uid) {
    return userRepository
        .findByToken(uid)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, message("user.not_found")));
  }

  private StopChangeRequestModel requireRequest(String token) {
    return repository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("stop_change.not_found")));
  }
}
