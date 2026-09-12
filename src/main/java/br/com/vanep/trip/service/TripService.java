package br.com.vanep.trip.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.dto.TripCreateRequestDTO;
import br.com.vanep.trip.dto.TripResponseDTO;
import br.com.vanep.trip.dto.TripUpdateRequestDTO;
import br.com.vanep.trip.enums.TripCoherenceViolation;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.mapper.TripMapper;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
public class TripService {

  public static final ZoneId SERVICE_ZONE = ZoneId.of("America/Sao_Paulo");

  private final TripRepository trips;
  private final DriverRepository drivers;
  private final UserService users;
  private final TripMapper mapper;
  private final TripTransitionPolicy transitions;
  private final TripCoherencePolicy coherence;
  private final WorkWindowPolicy workWindow;
  private final MessageSource messages;

  public TripService(
      TripRepository trips,
      DriverRepository drivers,
      UserService users,
      TripMapper mapper,
      TripTransitionPolicy transitions,
      TripCoherencePolicy coherence,
      WorkWindowPolicy workWindow,
      MessageSource messages) {
    this.trips = trips;
    this.drivers = drivers;
    this.users = users;
    this.mapper = mapper;
    this.transitions = transitions;
    this.coherence = coherence;
    this.workWindow = workWindow;
    this.messages = messages;
  }

  @Transactional
  public TripResponseDTO startToday(String callerUid, Shift shift) {
    DriverModel driver = requireApprovedDriver(callerUid);
    LocalDate serviceDate = today();

    TripModel trip =
        trips
            .findByDriverAndServiceDateAndShift(driver.getId(), serviceDate, shift)
            .orElseGet(() -> insertOrReread(driver, serviceDate, shift));

    if (trip.getStatus() == TripStatus.IN_PROGRESS) {
      return response(trip);
    }
    if (!transitions.canStart(trip.getStatus())) {
      throw conflict("trip.already_completed");
    }

    trip.setStatus(TripStatus.IN_PROGRESS);
    trip.setStartedAt(Instant.now());
    return response(trips.save(trip));
  }

  @Transactional
  public TripResponseDTO finishToday(String callerUid, Shift shift) {
    DriverModel driver = requireApprovedDriver(callerUid);
    TripModel trip =
        trips
            .findByDriverAndServiceDateAndShift(driver.getId(), today(), shift)
            .orElseThrow(() -> conflict("trip.not_started"));

    if (trip.getStatus() == TripStatus.COMPLETED) {
      return response(trip);
    }
    if (!transitions.canFinish(trip.getStatus())) {
      throw conflict("trip.not_started");
    }

    trip.setStatus(TripStatus.COMPLETED);
    trip.setFinishedAt(Instant.now());
    return response(trips.save(trip));
  }

  @Transactional(readOnly = true)
  public List<TripResponseDTO> findToday(String callerUid) {
    DriverModel driver = requireApprovedDriver(callerUid);
    return trips.findByDriverAndServiceDate(driver.getId(), today()).stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public TripResponseDTO create(TripCreateRequestDTO request) {
    DriverModel driver =
        drivers.findByToken(request.driverToken()).orElseThrow(() -> notFound("driver.not_found"));

    trips
        .findByDriverAndServiceDateAndShift(driver.getId(), request.serviceDate(), request.shift())
        .ifPresent(
            existing -> {
              throw conflict("trip.duplicate_slot");
            });

    TripStatus status = request.status() != null ? request.status() : TripStatus.SCHEDULED;
    requireCoherent(status, request.startedAt(), request.finishedAt());

    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(request.serviceDate());
    trip.setShift(request.shift());
    trip.setStatus(status);
    trip.setStartedAt(request.startedAt());
    trip.setFinishedAt(request.finishedAt());
    return response(trips.save(trip));
  }

  @Transactional(readOnly = true)
  public Page<TripResponseDTO> findAll(String driverToken, Pageable pageable) {
    Long driverId =
        driverToken == null || driverToken.isBlank()
            ? null
            : drivers.findByToken(driverToken).map(DriverModel::getId).orElse(-1L);
    return trips.findPage(driverId, pageable).map(this::response);
  }

  @Transactional(readOnly = true)
  public TripResponseDTO findByToken(String token) {
    return response(requireTrip(token));
  }

  @Transactional
  public TripResponseDTO update(String token, TripUpdateRequestDTO request) {
    TripModel trip = requireTrip(token);

    if (request.shift().isPresent()) {
      Shift shift = request.shift().get();
      if (shift == null) {
        throw badRequest("trip.field.null");
      }
      trip.setShift(shift);
    }
    if (request.status().isPresent()) {
      TripStatus status = request.status().get();
      if (status == null) {
        throw badRequest("trip.field.null");
      }
      trip.setStatus(status);
    }
    if (request.startedAt().isPresent()) {
      trip.setStartedAt(request.startedAt().get());
    }
    if (request.finishedAt().isPresent()) {
      trip.setFinishedAt(request.finishedAt().get());
    }

    requireCoherent(trip.getStatus(), trip.getStartedAt(), trip.getFinishedAt());
    return response(trips.save(trip));
  }

  @Transactional
  public void delete(String token) {
    trips.delete(requireTrip(token));
  }

  @Transactional
  public TripResponseDTO restore(String token) {
    if (!trips.existsDeletedByToken(token)) {
      throw notFound("trip.not_found");
    }
    trips.restoreByToken(token);
    return response(requireTrip(token));
  }

  private void requireCoherent(TripStatus status, Instant startedAt, Instant finishedAt) {
    coherence
        .validate(status, startedAt, finishedAt)
        .map(TripCoherenceViolation::messageKey)
        .ifPresent(
            key -> {
              throw badRequest(key);
            });
  }

  TripModel insertOrReread(DriverModel driver, LocalDate serviceDate, Shift shift) {
    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(serviceDate);
    trip.setShift(shift);
    try {
      return trips.saveAndFlush(trip);
    } catch (DataIntegrityViolationException lostTheRace) {
      return trips
          .findByDriverAndServiceDateAndShift(driver.getId(), serviceDate, shift)
          .orElseThrow(() -> conflict("trip.duplicate_slot"));
    }
  }

  LocalDate today() {
    return LocalDate.now(SERVICE_ZONE);
  }

  private TripResponseDTO response(TripModel trip) {
    DriverModel driver = trip.getDriver();
    LocalDateTime startedAt =
        trip.getStartedAt() == null
            ? null
            : LocalDateTime.ofInstant(trip.getStartedAt(), SERVICE_ZONE);
    boolean outside =
        workWindow.isOutsideWorkWindow(
            driver.getWorkDays(), driver.getWorkStartTime(), driver.getWorkEndTime(), startedAt);
    return mapper.toResponse(trip, outside);
  }

  private TripModel requireTrip(String token) {
    return trips.findByToken(token).orElseThrow(() -> notFound("trip.not_found"));
  }

  private DriverModel requireApprovedDriver(String callerUid) {
    UserModel user = users.requireByTokenAndType(callerUid, UserType.DRIVER);
    DriverModel driver =
        drivers
            .findByUserId(user.getId())
            .orElseThrow(() -> notFound("user.driver_profile.not_found"));
    if (driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, message("driver.not_approved"));
    }
    return driver;
  }

  private ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  private ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  private ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
