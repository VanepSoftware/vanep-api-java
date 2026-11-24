package br.com.vanep.clientday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientday.dto.ClientDayResponseDTO;
import br.com.vanep.clientday.dto.ClientDayRiderDTO;
import br.com.vanep.clientday.dto.ClientDayUpcomingDTO;
import br.com.vanep.clientday.dto.ClientDayVanDTO;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.contract.model.ContractItemModel;
import br.com.vanep.contract.model.ContractModel;
import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.service.RoutePassengerQueryService;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientDayServiceTest {

  private static final String CALLER = "client-uid";

  /** 2027-03-02 02:00 UTC is still Monday 2027-03-01 in America/Sao_Paulo. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2027-03-02T02:00:00Z"), ZoneOffset.UTC);

  private static final LocalDate MONDAY = LocalDate.of(2027, 3, 1);

  @Mock private UserService users;
  @Mock private ClientRepository clients;
  @Mock private ContractItemRepository items;
  @Mock private VehicleRepository vehicles;
  @Mock private RoutePassengerQueryService passengers;
  @Mock private MessageSource messages;

  private ClientDayService service;
  private DriverModel carlos;
  private DriverModel rita;

  @BeforeEach
  void setUp() {
    service = new ClientDayService(users, clients, items, vehicles, passengers, messages, CLOCK);

    UserModel user = new UserModel();
    user.setId(1L);
    when(users.requireByTokenAndType(CALLER, UserType.CLIENT)).thenReturn(user);
    ClientModel client = new ClientModel();
    client.setId(10L);
    when(clients.findByUserId(1L)).thenReturn(Optional.of(client));

    carlos = driver(7L, "drv-carlos", "Carlos Souza");
    rita = driver(8L, "drv-rita", "Rita Lima");
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of(vehicle("Sprinter", "SDF4E21"), vehicle("Ducato", "PBX7C19")));
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(8L)))
        .thenReturn(List.of());
    when(passengers.findPassengers(any(), eq(MONDAY), any())).thenReturn(List.of());
  }

  @Test
  void groupsTodaysSlotsByDriverAndShift() {
    DependentModel pedro = dependent("dep-pedro", "Pedro");
    DependentModel ana = dependent("dep-ana", "Ana");
    givenItems(
        item(
            carlos,
            pedro,
            slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 6, 50),
            slot(DayOfWeek.MONDAY, RouteLeg.RETURN, OperationShift.MORNING, 12, 30),
            slot(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 6, 0)),
        item(carlos, ana, slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 6, 40)),
        item(
            rita,
            ana,
            slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, OperationShift.AFTERNOON, 11, 40)));
    when(passengers.findPassengers(carlos, MONDAY, OperationShift.MORNING))
        .thenReturn(
            List.of(
                passenger(RouteLeg.OUTBOUND, 6, 20),
                passenger(RouteLeg.RETURN, 12, 30),
                passenger(RouteLeg.OUTBOUND, 6, 40)));

    ClientDayResponseDTO day = service.findMyDay(CALLER);

    assertThat(day.date()).isEqualTo(MONDAY);
    assertThat(day.vans()).hasSize(2);

    ClientDayVanDTO morning = day.vans().get(0);
    assertThat(morning.driverName()).isEqualTo("Carlos Souza");
    assertThat(morning.vehicleModel()).isEqualTo("Sprinter");
    assertThat(morning.plate()).isEqualTo("SDF4E21");
    assertThat(morning.shift()).isEqualTo(OperationShift.MORNING);
    assertThat(morning.departureTime()).isEqualTo(LocalTime.of(6, 20));
    assertThat(morning.riders())
        .extracting(ClientDayRiderDTO::name)
        .containsExactly("Ana", "Pedro");
    assertThat(morning.boardingStart()).isEqualTo(LocalTime.of(6, 40));
    assertThat(morning.boardingEnd()).isEqualTo(LocalTime.of(6, 50));
    assertThat(morning.schoolDeparture()).isEqualTo(LocalTime.of(12, 30));

    assertThat(day.upcoming()).isEmpty();

    ClientDayVanDTO afternoon = day.vans().get(1);
    assertThat(afternoon.driverName()).isEqualTo("Rita Lima");
    assertThat(afternoon.vehicleModel()).isNull();
    assertThat(afternoon.plate()).isNull();
    assertThat(afternoon.departureTime())
        .as("falls back to the family's own pickup when the route is empty")
        .isEqualTo(LocalTime.of(11, 40));
    assertThat(afternoon.schoolDeparture()).isNull();
  }

  @Test
  void aChildWhoOnlyRidesBackHasNoBoardingWindow() {
    givenItems(
        item(
            carlos,
            dependent("dep-pedro", "Pedro"),
            slot(DayOfWeek.MONDAY, RouteLeg.RETURN, OperationShift.MORNING, 12, 30)));
    when(passengers.findPassengers(carlos, MONDAY, OperationShift.MORNING))
        .thenReturn(List.of(passenger(RouteLeg.RETURN, 12, 10)));

    ClientDayVanDTO van = service.findMyDay(CALLER).vans().getFirst();

    assertThat(van.boardingStart()).isNull();
    assertThat(van.boardingEnd()).isNull();
    assertThat(van.schoolDeparture()).isEqualTo(LocalTime.of(12, 30));
    assertThat(van.departureTime()).isEqualTo(LocalTime.of(12, 10));
  }

  @Test
  void listsTheNextSchoolDaysWhenNoChildRidesToday() {
    givenItems(
        item(
            carlos,
            dependent("dep-pedro", "Pedro"),
            slot(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 6, 50),
            slot(DayOfWeek.TUESDAY, RouteLeg.RETURN, OperationShift.MORNING, 12, 30),
            slot(DayOfWeek.THURSDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 7, 0)));

    ClientDayResponseDTO day = service.findMyDay(CALLER);

    assertThat(day.vans()).isEmpty();
    assertThat(day.upcoming())
        .extracting(
            ClientDayUpcomingDTO::date,
            ClientDayUpcomingDTO::going,
            ClientDayUpcomingDTO::returning,
            ClientDayUpcomingDTO::firstPickup)
        .containsExactly(
            tuple(MONDAY.plusDays(1), true, true, LocalTime.of(6, 50)),
            tuple(MONDAY.plusDays(2), false, false, null),
            tuple(MONDAY.plusDays(3), true, false, LocalTime.of(7, 0)),
            tuple(MONDAY.plusDays(4), false, false, null),
            tuple(MONDAY.plusDays(7), false, false, null));
  }

  @Test
  void slidesTheWindowSoALaterNextRouteIsShown() {
    ContractItemModel late =
        item(
            carlos,
            dependent("dep-pedro", "Pedro"),
            slot(DayOfWeek.WEDNESDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 6, 50));
    late.getContract().setStartsOn(MONDAY.plusDays(8));
    givenItems(late);

    assertThat(service.findMyDay(CALLER).upcoming())
        .hasSize(5)
        .last()
        .satisfies(
            last -> {
              assertThat(last.date()).isEqualTo(MONDAY.plusDays(9));
              assertThat(last.going()).isTrue();
            });
  }

  @Test
  void showsAWeekendDayOnlyWhenSomeoneRides() {
    givenItems(
        item(
            carlos,
            dependent("dep-pedro", "Pedro"),
            slot(DayOfWeek.SATURDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, 8, 0)));

    assertThat(service.findMyDay(CALLER).upcoming())
        .extracting(ClientDayUpcomingDTO::date)
        .contains(MONDAY.plusDays(5))
        .doesNotContain(MONDAY.plusDays(6));
  }

  @Test
  void hasNothingUpcomingWithoutAnyRideAhead() {
    givenItems();

    ClientDayResponseDTO day = service.findMyDay(CALLER);

    assertThat(day.vans()).isEmpty();
    assertThat(day.upcoming()).isEmpty();
  }

  @Test
  void refusesACallerWithoutClientProfile() {
    when(clients.findByUserId(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findMyDay(CALLER))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  private void givenItems(ContractItemModel... contractItems) {
    when(items.findActiveOfClientBetween(10L, MONDAY, MONDAY.plusDays(14)))
        .thenReturn(List.of(contractItems));
  }

  private static DriverModel driver(Long id, String token, String name) {
    UserModel user = new UserModel();
    user.setName(name);
    DriverModel driver = new DriverModel();
    driver.setId(id);
    driver.setToken(token);
    driver.setUser(user);
    return driver;
  }

  private static DependentModel dependent(String token, String name) {
    DependentModel dependent = new DependentModel();
    dependent.setToken(token);
    dependent.setName(name);
    return dependent;
  }

  private static VehicleModel vehicle(String model, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setModel(model);
    vehicle.setPlate(plate);
    return vehicle;
  }

  private static ContractItemModel item(
      DriverModel driver, DependentModel dependent, ScheduleSlotModel... slots) {
    ClientDriverModel link = new ClientDriverModel();
    link.setDriver(driver);
    ContractModel contract = new ContractModel();
    contract.setClientDriver(link);
    contract.setStartsOn(MONDAY.minusMonths(1));
    contract.setEndsOn(MONDAY.plusMonths(2));
    ScheduleModel schedule = new ScheduleModel();
    for (ScheduleSlotModel slot : slots) {
      schedule.addSlot(slot);
    }
    ContractItemModel item = new ContractItemModel();
    item.setContract(contract);
    item.setDependent(dependent);
    item.setSchedule(schedule);
    return item;
  }

  private static ScheduleSlotModel slot(
      DayOfWeek weekday, RouteLeg leg, OperationShift shift, int hour, int minute) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(weekday);
    slot.setLeg(leg);
    slot.setShift(shift);
    slot.setPickupTime(LocalTime.of(hour, minute));
    return slot;
  }

  private static RoutePassengerDTO passenger(RouteLeg leg, int hour, int minute) {
    return new RoutePassengerDTO(
        null,
        null,
        "Aluno",
        null,
        null,
        null,
        leg,
        LocalTime.of(hour, minute),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
