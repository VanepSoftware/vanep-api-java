package br.com.vanep.assistant.service;

import br.com.vanep.assistant.dto.AssistantVanResponseDTO;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO;
import br.com.vanep.driveroccupancy.service.DriverOccupancyService;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.service.RoutePassengerQueryService;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the "my vans" list of an assistant: one item per shift the linked driver operates, shown
 * on the driver's main (oldest active) vehicle, because contracts and trips are per driver and
 * shift, not per vehicle.
 */
@Service
public class AssistantVansService {

  private final AssistantRepository assistantRepository;
  private final UserService userService;
  private final VehicleRepository vehicleRepository;
  private final DriverOccupancyService occupancyService;
  private final RoutePassengerQueryService passengerService;
  private final TripRepository tripRepository;
  private final MessageSource messages;
  private final Clock clock;

  public AssistantVansService(
      AssistantRepository assistantRepository,
      UserService userService,
      VehicleRepository vehicleRepository,
      DriverOccupancyService occupancyService,
      RoutePassengerQueryService passengerService,
      TripRepository tripRepository,
      MessageSource messages,
      Clock clock) {
    this.assistantRepository = assistantRepository;
    this.userService = userService;
    this.vehicleRepository = vehicleRepository;
    this.occupancyService = occupancyService;
    this.passengerService = passengerService;
    this.tripRepository = tripRepository;
    this.messages = messages;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AssistantVanResponseDTO> listMine(String callerUid) {
    AssistantModel assistant = resolveAssistant(callerUid);
    DriverModel driver = assistant.getDriver();
    if (assistant.getStatus() != AssistantStatus.ACTIVE || driver == null) {
      return List.of();
    }

    List<VehicleModel> vehicles =
        vehicleRepository.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(
            List.of(driver.getId()));
    if (vehicles.isEmpty()) {
      return List.of();
    }
    VehicleModel mainVehicle = vehicles.getFirst();
    LocalDate today = LocalDate.now(clock.withZone(TripService.SERVICE_ZONE));

    List<ShiftOccupancyDTO> roster =
        occupancyService.findOccupancy(driver, null, today).shifts().stream()
            .filter(shift -> shift.passengers() > 0)
            .toList();
    if (roster.isEmpty()) {
      return List.of(toItem(mainVehicle, driver, null, null, 0, false));
    }

    return roster.stream().map(shift -> summarizeShift(mainVehicle, driver, shift, today)).toList();
  }

  private AssistantVanResponseDTO summarizeShift(
      VehicleModel vehicle, DriverModel driver, ShiftOccupancyDTO shift, LocalDate today) {
    List<RoutePassengerDTO> passengersToday =
        passengerService.findPassengers(driver, today, shift.shift());
    LocalTime departure =
        passengersToday.stream()
            .map(RoutePassengerDTO::pickupTime)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
    boolean hasRouteToday =
        !passengersToday.isEmpty() || hasTripToday(driver, today, shift.shift());
    return toItem(vehicle, driver, shift.shift(), departure, shift.passengers(), hasRouteToday);
  }

  private boolean hasTripToday(DriverModel driver, LocalDate today, OperationShift shift) {
    return tripRepository
        .findByDriverAndServiceDateAndShift(driver.getId(), today, shift)
        .filter(trip -> trip.getStatus() != TripStatus.CANCELLED)
        .isPresent();
  }

  private AssistantVanResponseDTO toItem(
      VehicleModel vehicle,
      DriverModel driver,
      OperationShift shift,
      LocalTime departure,
      long studentCount,
      boolean hasRouteToday) {
    return new AssistantVanResponseDTO(
        vehicle.getToken(),
        driver.getToken(),
        driver.getUser().getName(),
        vehicle.getPlate(),
        vehicle.getModel(),
        shift,
        departure,
        studentCount,
        hasRouteToday);
  }

  private AssistantModel resolveAssistant(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.ASSISTANT);
    return assistantRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messages.getMessage(
                        "assistant.profile.not_found", null, LocaleContextHolder.getLocale())));
  }
}
