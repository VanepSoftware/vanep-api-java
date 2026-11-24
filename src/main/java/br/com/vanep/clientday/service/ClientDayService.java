package br.com.vanep.clientday.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientday.dto.ClientDayChildDTO;
import br.com.vanep.clientday.dto.ClientDayResponseDTO;
import br.com.vanep.clientday.dto.ClientDayRiderDTO;
import br.com.vanep.clientday.dto.ClientDayUpcomingDTO;
import br.com.vanep.clientday.dto.ClientDayVanDTO;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.service.RoutePassengerQueryService;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The client's day with the vans: one entry per driver and shift in which at least one of the
 * client's children rides today, from the ACTIVE contracts in force and the weekday of their
 * schedules.
 */
@Service
public class ClientDayService {

  static final int DAYS_TO_LOOK_AHEAD = 14;
  static final int UPCOMING_DAYS_SHOWN = 5;

  private final UserService userService;
  private final ClientRepository clientRepository;
  private final ContractItemRepository contractItems;
  private final DependentRepository dependentRepository;
  private final VehicleRepository vehicleRepository;
  private final RoutePassengerQueryService passengerService;
  private final MessageSource messages;
  private final Clock clock;

  public ClientDayService(
      UserService userService,
      ClientRepository clientRepository,
      ContractItemRepository contractItems,
      DependentRepository dependentRepository,
      VehicleRepository vehicleRepository,
      RoutePassengerQueryService passengerService,
      MessageSource messages,
      Clock clock) {
    this.userService = userService;
    this.clientRepository = clientRepository;
    this.contractItems = contractItems;
    this.dependentRepository = dependentRepository;
    this.vehicleRepository = vehicleRepository;
    this.passengerService = passengerService;
    this.messages = messages;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ClientDayResponseDTO findMyDay(String callerUid) {
    ClientModel client = resolveClient(callerUid);
    LocalDate today = LocalDate.now(clock.withZone(TripService.SERVICE_ZONE));

    List<ContractItemModel> items =
        contractItems.findActiveOfClientBetween(
            client.getId(), today, today.plusDays(DAYS_TO_LOOK_AHEAD));

    Map<VanKey, List<RiderSlot>> slotsByVan = new LinkedHashMap<>();
    for (ContractItemModel item : items) {
      DriverModel driver = item.getContract().getClientDriver().getDriver();
      for (ScheduleSlotModel slot : slotsOn(item, today)) {
        slotsByVan
            .computeIfAbsent(new VanKey(driver, slot.getShift()), key -> new ArrayList<>())
            .add(new RiderSlot(item.getDependent(), slot.getLeg(), slot.getPickupTime()));
      }
    }

    List<ClientDayVanDTO> vans =
        slotsByVan.entrySet().stream()
            .map(entry -> toVan(entry.getKey(), entry.getValue(), today))
            .sorted(
                Comparator.comparing(
                    ClientDayVanDTO::departureTime,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    List<ClientDayUpcomingDTO> upcoming = vans.isEmpty() ? upcomingDays(items, today) : List.of();
    return new ClientDayResponseDTO(
        today, vans, upcoming, childrenWithoutVan(client, items, today));
  }

  /**
   * Dependents of the client that no contract in force today covers. The client registered as their
   * own passenger is left out: they are not a child waiting for a van.
   */
  private List<ClientDayChildDTO> childrenWithoutVan(
      ClientModel client, List<ContractItemModel> items, LocalDate today) {
    Set<Long> covered =
        items.stream()
            .filter(item -> isInForce(item.getContract(), today))
            .map(item -> item.getDependent().getId())
            .collect(Collectors.toSet());
    return dependentRepository.findByClientId(client.getId()).stream()
        .filter(dependent -> !dependent.isSelf() && !covered.contains(dependent.getId()))
        .sorted(Comparator.comparing(DependentModel::getName))
        .map(dependent -> new ClientDayChildDTO(dependent.getToken(), dependent.getName()))
        .toList();
  }

  private static boolean isInForce(ContractModel contract, LocalDate date) {
    return !contract.getStartsOn().isAfter(date) && !contract.getEndsOn().isBefore(date);
  }

  /**
   * The next {@value #UPCOMING_DAYS_SHOWN} school days after {@code today}: every Monday to Friday,
   * plus weekend days on which some child rides. Empty when nobody rides within the look-ahead
   * window.
   */
  private static List<ClientDayUpcomingDTO> upcomingDays(
      List<ContractItemModel> items, LocalDate today) {
    List<ClientDayUpcomingDTO> days =
        IntStream.rangeClosed(1, DAYS_TO_LOOK_AHEAD)
            .mapToObj(offset -> upcomingDay(items, today.plusDays(offset)))
            .filter(day -> day.date().getDayOfWeek().getValue() <= 5 || day.firstPickup() != null)
            .toList();
    int firstRide =
        IntStream.range(0, days.size())
            .filter(index -> days.get(index).going() || days.get(index).returning())
            .findFirst()
            .orElse(-1);
    if (firstRide < 0) {
      return List.of();
    }
    // Keeps the next route visible even when it is further than the days shown.
    int from = Math.max(0, firstRide - UPCOMING_DAYS_SHOWN + 1);
    return days.subList(from, Math.min(days.size(), from + UPCOMING_DAYS_SHOWN));
  }

  private static ClientDayUpcomingDTO upcomingDay(List<ContractItemModel> items, LocalDate date) {
    List<ScheduleSlotModel> slots =
        items.stream().flatMap(item -> slotsOn(item, date).stream()).toList();
    return new ClientDayUpcomingDTO(
        date,
        slots.stream().anyMatch(slot -> slot.getLeg() == RouteLeg.OUTBOUND),
        slots.stream().anyMatch(slot -> slot.getLeg() == RouteLeg.RETURN),
        slots.stream()
            .map(ScheduleSlotModel::getPickupTime)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null));
  }

  /** The slots of the item on {@code date}, none when its contract is not in force then. */
  private static List<ScheduleSlotModel> slotsOn(ContractItemModel item, LocalDate date) {
    ContractModel contract = item.getContract();
    if (!isInForce(contract, date)) {
      return List.of();
    }
    return item.getSchedule().getSlots().stream()
        .filter(slot -> slot.getWeekday() == date.getDayOfWeek())
        .toList();
  }

  private ClientDayVanDTO toVan(VanKey key, List<RiderSlot> slots, LocalDate today) {
    DriverModel driver = key.driver();
    Optional<VehicleModel> vehicle = mainVehicleOf(driver);

    Map<String, ClientDayRiderDTO> riders = new LinkedHashMap<>();
    slots.stream()
        .sorted(
            Comparator.comparing(
                RiderSlot::pickupTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            slot ->
                riders.putIfAbsent(
                    slot.dependent().getToken(),
                    new ClientDayRiderDTO(
                        slot.dependent().getToken(), slot.dependent().getName())));

    LocalTime boardingStart = earliest(slots, RouteLeg.OUTBOUND);
    LocalTime boardingEnd =
        slots.stream()
            .filter(slot -> slot.leg() == RouteLeg.OUTBOUND)
            .map(RiderSlot::pickupTime)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    LocalTime schoolDeparture = earliest(slots, RouteLeg.RETURN);

    return new ClientDayVanDTO(
        driver.getToken(),
        driver.getUser().getName(),
        vehicle.map(VehicleModel::getModel).orElse(null),
        vehicle.map(VehicleModel::getPlate).orElse(null),
        key.shift(),
        departureOf(driver, key.shift(), today, boardingStart, schoolDeparture),
        List.copyOf(riders.values()),
        boardingStart,
        boardingEnd,
        schoolDeparture);
  }

  /**
   * First pickup of the driver's whole route in the shift today, so the family sees when the van
   * leaves; falls back to the family's own first pickup.
   */
  private LocalTime departureOf(
      DriverModel driver,
      OperationShift shift,
      LocalDate today,
      LocalTime boardingStart,
      LocalTime schoolDeparture) {
    RouteLeg leg = boardingStart != null ? RouteLeg.OUTBOUND : RouteLeg.RETURN;
    return passengerService.findPassengers(driver, today, shift).stream()
        .filter(passenger -> passenger.leg() == leg)
        .map(RoutePassengerDTO::pickupTime)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .orElse(boardingStart != null ? boardingStart : schoolDeparture);
  }

  private Optional<VehicleModel> mainVehicleOf(DriverModel driver) {
    return vehicleRepository
        .findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(driver.getId()))
        .stream()
        .findFirst();
  }

  private static LocalTime earliest(List<RiderSlot> slots, RouteLeg leg) {
    return slots.stream()
        .filter(slot -> slot.leg() == leg)
        .map(RiderSlot::pickupTime)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .orElse(null);
  }

  private ClientModel resolveClient(String callerUid) {
    UserModel user = userService.requireByTokenAndType(callerUid, UserType.CLIENT);
    return clientRepository
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messages.getMessage(
                        "client.profile.not_found", null, LocaleContextHolder.getLocale())));
  }

  /** Drivers are compared by id: the same driver may come from several contracts. */
  private record VanKey(Long driverId, OperationShift shift, DriverModel driver) {
    VanKey(DriverModel driver, OperationShift shift) {
      this(driver.getId(), shift, driver);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof VanKey key && driverId.equals(key.driverId) && shift == key.shift;
    }

    @Override
    public int hashCode() {
      return Objects.hash(driverId, shift);
    }
  }

  private record RiderSlot(DependentModel dependent, RouteLeg leg, LocalTime pickupTime) {}
}
