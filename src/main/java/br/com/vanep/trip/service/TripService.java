package br.com.vanep.trip.service;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final TripTransitionPolicy transitions;
  private final MessageSource messages;

  public TripService(
      TripRepository trips,
      DriverRepository drivers,
      UserService users,
      TripTransitionPolicy transitions,
      MessageSource messages) {
    this.trips = trips;
    this.drivers = drivers;
    this.users = users;
    this.transitions = transitions;
    this.messages = messages;
  }

  @Transactional
  public TripModel startToday(String callerUid, Shift shift) {
    DriverModel driver = requireApprovedDriver(callerUid);
    LocalDate serviceDate = today();

    TripModel trip =
        trips
            .findByDriverAndServiceDateAndShift(driver.getId(), serviceDate, shift)
            .orElseGet(() -> insertOrReread(driver, serviceDate, shift));

    if (trip.getStatus() == TripStatus.IN_PROGRESS) {
      return trip;
    }
    if (!transitions.canStart(trip.getStatus())) {
      throw conflict("trip.already_completed");
    }

    trip.setStatus(TripStatus.IN_PROGRESS);
    trip.setStartedAt(Instant.now());
    return trips.save(trip);
  }

  @Transactional
  public TripModel finishToday(String callerUid, Shift shift) {
    DriverModel driver = requireApprovedDriver(callerUid);
    TripModel trip =
        trips
            .findByDriverAndServiceDateAndShift(driver.getId(), today(), shift)
            .orElseThrow(() -> conflict("trip.not_started"));

    if (trip.getStatus() == TripStatus.COMPLETED) {
      return trip;
    }
    if (!transitions.canFinish(trip.getStatus())) {
      throw conflict("trip.not_started");
    }

    trip.setStatus(TripStatus.COMPLETED);
    trip.setFinishedAt(Instant.now());
    return trips.save(trip);
  }

  @Transactional(readOnly = true)
  public List<TripModel> findToday(String callerUid) {
    DriverModel driver = requireApprovedDriver(callerUid);
    return trips.findByDriverAndServiceDate(driver.getId(), today());
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

  DriverModel requireApprovedDriver(String callerUid) {
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

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
