package br.com.vanep.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.assistant.dto.AssistantStudentResponseDTO;
import br.com.vanep.assistant.dto.AssistantVanResponseDTO;
import br.com.vanep.assistant.enums.AssistantStatus;
import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driveroccupancy.dto.DriverOccupancyResponseDTO;
import br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO;
import br.com.vanep.driveroccupancy.service.DriverOccupancyService;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.routepassenger.service.RoutePassengerQueryService;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.time.Clock;
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
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssistantVansServiceTest {

  private static final String CALLER = "assistant-uid";

  /** 2027-03-01 01:00 UTC is still 2027-02-28 in America/Sao_Paulo. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2027-03-01T01:00:00Z"), ZoneOffset.UTC);

  private static final LocalDate TODAY = LocalDate.of(2027, 2, 28);

  @Mock private AssistantRepository assistants;
  @Mock private UserService users;
  @Mock private VehicleRepository vehicles;
  @Mock private DriverOccupancyService occupancy;
  @Mock private RoutePassengerQueryService passengers;
  @Mock private TripRepository trips;
  @Mock private MessageSource messages;

  private AssistantVansService service;
  private AssistantModel assistant;
  private DriverModel driver;
  private VehicleModel mainVehicle;

  @BeforeEach
  void setUp() {
    service =
        new AssistantVansService(
            assistants, users, vehicles, occupancy, passengers, trips, messages, CLOCK);

    UserModel assistantUser = new UserModel();
    assistantUser.setId(1L);
    when(users.requireByTokenAndType(CALLER, UserType.ASSISTANT)).thenReturn(assistantUser);

    UserModel driverUser = new UserModel();
    driverUser.setName("Carlos Mendes");
    driver = new DriverModel();
    driver.setId(7L);
    driver.setToken("driver-carlos");
    driver.setUser(driverUser);

    assistant = new AssistantModel();
    assistant.setDriver(driver);
    assistant.setStatus(AssistantStatus.ACTIVE);
    when(assistants.findByUserId(1L)).thenReturn(Optional.of(assistant));

    mainVehicle = vehicle("van-sprinter", "SDF4E21", "Sprinter");
  }

  @Test
  void listsOneItemPerShiftWithPassengersOnTheMainVehicle() {
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of(mainVehicle, vehicle("van-ducato", "PBX7C19", "Ducato")));
    givenRoster(
        new ShiftOccupancyDTO(OperationShift.MORNING, 16L),
        new ShiftOccupancyDTO(OperationShift.AFTERNOON, 9L),
        new ShiftOccupancyDTO(OperationShift.NIGHT, 0L));
    when(passengers.findPassengers(driver, TODAY, OperationShift.MORNING))
        .thenReturn(List.of(pickupAt(LocalTime.of(6, 40)), pickupAt(LocalTime.of(6, 20))));
    when(passengers.findPassengers(driver, TODAY, OperationShift.AFTERNOON)).thenReturn(List.of());
    when(trips.findByDriverAndServiceDateAndShift(7L, TODAY, OperationShift.AFTERNOON))
        .thenReturn(Optional.empty());

    List<AssistantVanResponseDTO> vans = service.listMine(CALLER);

    assertThat(vans)
        .extracting(
            AssistantVanResponseDTO::token,
            AssistantVanResponseDTO::driverName,
            AssistantVanResponseDTO::plate,
            AssistantVanResponseDTO::shift,
            AssistantVanResponseDTO::departureTime,
            AssistantVanResponseDTO::studentCount,
            AssistantVanResponseDTO::hasRouteToday)
        .containsExactly(
            tuple(
                "van-sprinter",
                "Carlos Mendes",
                "SDF4E21",
                OperationShift.MORNING,
                LocalTime.of(6, 20),
                16L,
                true),
            tuple(
                "van-sprinter",
                "Carlos Mendes",
                "SDF4E21",
                OperationShift.AFTERNOON,
                null,
                9L,
                false));
  }

  @Test
  void aScheduledTripCountsAsRouteTodayEvenWithoutPassengers() {
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of(mainVehicle));
    givenRoster(new ShiftOccupancyDTO(OperationShift.MORNING, 3L));
    when(passengers.findPassengers(driver, TODAY, OperationShift.MORNING)).thenReturn(List.of());
    TripModel trip = new TripModel();
    trip.setStatus(TripStatus.SCHEDULED);
    when(trips.findByDriverAndServiceDateAndShift(7L, TODAY, OperationShift.MORNING))
        .thenReturn(Optional.of(trip));

    assertThat(service.listMine(CALLER)).singleElement().matches(van -> van.hasRouteToday());
  }

  @Test
  void aCancelledTripIsNoRouteToday() {
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of(mainVehicle));
    givenRoster(new ShiftOccupancyDTO(OperationShift.MORNING, 3L));
    when(passengers.findPassengers(driver, TODAY, OperationShift.MORNING)).thenReturn(List.of());
    TripModel trip = new TripModel();
    trip.setStatus(TripStatus.CANCELLED);
    when(trips.findByDriverAndServiceDateAndShift(7L, TODAY, OperationShift.MORNING))
        .thenReturn(Optional.of(trip));

    assertThat(service.listMine(CALLER)).singleElement().matches(van -> !van.hasRouteToday());
  }

  @Test
  void keepsTheVanVisibleWhenNoShiftHasStudentsYet() {
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of(mainVehicle));
    givenRoster(new ShiftOccupancyDTO(OperationShift.MORNING, 0L));

    assertThat(service.listMine(CALLER))
        .singleElement()
        .satisfies(
            van -> {
              assertThat(van.token()).isEqualTo("van-sprinter");
              assertThat(van.shift()).isNull();
              assertThat(van.studentCount()).isZero();
              assertThat(van.hasRouteToday()).isFalse();
            });
  }

  @Test
  void isEmptyWhenTheDriverHasNoActiveVehicle() {
    when(vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(List.of(7L)))
        .thenReturn(List.of());

    assertThat(service.listMine(CALLER)).isEmpty();
    verifyNoInteractions(occupancy, passengers, trips);
  }

  @Test
  void isEmptyWhileTheLinkIsPaused() {
    assistant.setStatus(AssistantStatus.INACTIVE);

    assertThat(service.listMine(CALLER)).isEmpty();
    verifyNoInteractions(vehicles, occupancy, passengers, trips);
  }

  @Test
  void isEmptyWithoutALinkedDriver() {
    assistant.setStatus(AssistantStatus.UNLINKED);
    assistant.setDriver(null);

    assertThat(service.listMine(CALLER)).isEmpty();
    verifyNoInteractions(vehicles);
  }

  private void givenRoster(ShiftOccupancyDTO... shifts) {
    when(occupancy.findOccupancy(driver, null, TODAY))
        .thenReturn(new DriverOccupancyResponseDTO(List.of(shifts), 0L, 0L));
  }

  private static VehicleModel vehicle(String token, String plate, String model) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setToken(token);
    vehicle.setPlate(plate);
    vehicle.setModel(model);
    return vehicle;
  }

  private static RoutePassengerDTO pickupAt(LocalTime time) {
    return new RoutePassengerDTO(
        null, null, "Aluno", null, null, null, null, time, null, null, null, null, null, null, null,
        null);
  }

  @Test
  void listsTheStudentsOfAShiftOncePerPassengerInPickupOrder() {
    givenVehicleOf(driver, true);
    when(passengers.findPassengers(driver, TODAY, OperationShift.MORNING))
        .thenReturn(
            List.of(
                passenger(
                    PassengerSource.CONTRACT_ITEM,
                    "p-ana",
                    "Ana",
                    RouteLeg.RETURN,
                    LocalTime.of(12, 0),
                    "Centro",
                    "Escola A"),
                passenger(
                    PassengerSource.UNLINKED_PASSENGER,
                    "p-bia",
                    "Bia",
                    RouteLeg.OUTBOUND,
                    LocalTime.of(6, 40),
                    "  ",
                    "Escola B"),
                passenger(
                    PassengerSource.CONTRACT_ITEM,
                    "p-ana",
                    "Ana",
                    RouteLeg.OUTBOUND,
                    LocalTime.of(6, 20),
                    "Centro",
                    "Escola A")));

    List<AssistantStudentResponseDTO> students =
        service.listStudents(CALLER, "van-sprinter", OperationShift.MORNING);

    assertThat(students)
        .extracting(
            AssistantStudentResponseDTO::token,
            AssistantStudentResponseDTO::stopNumber,
            AssistantStudentResponseDTO::placeName,
            AssistantStudentResponseDTO::hasFamilyInApp)
        .containsExactly(tuple("p-ana", 1, "Centro", true), tuple("p-bia", 2, "Escola B", false));
  }

  @Test
  void looksAheadToTheNextDayTheShiftRunsWhenItDoesNotRunToday() {
    givenVehicleOf(driver, true);
    when(passengers.findPassengers(driver, TODAY, OperationShift.MORNING)).thenReturn(List.of());
    when(passengers.findPassengers(driver, TODAY.plusDays(1), OperationShift.MORNING))
        .thenReturn(List.of());
    when(passengers.findPassengers(driver, TODAY.plusDays(2), OperationShift.MORNING))
        .thenReturn(
            List.of(
                passenger(
                    PassengerSource.CONTRACT_ITEM,
                    "p-ana",
                    "Ana",
                    RouteLeg.OUTBOUND,
                    LocalTime.of(6, 20),
                    "Centro",
                    "Escola A")));

    assertThat(service.listStudents(CALLER, "van-sprinter", OperationShift.MORNING))
        .extracting(AssistantStudentResponseDTO::token)
        .containsExactly("p-ana");
  }

  @Test
  void refusesAVehicleOfAnotherDriver() {
    DriverModel other = new DriverModel();
    other.setId(99L);
    givenVehicleOf(other, true);

    assertThatThrownBy(() -> service.listStudents(CALLER, "van-sprinter", OperationShift.MORNING))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verifyNoInteractions(passengers);
  }

  @Test
  void refusesAnInactiveVehicle() {
    givenVehicleOf(driver, false);

    assertThatThrownBy(() -> service.listStudents(CALLER, "van-sprinter", OperationShift.MORNING))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void refusesWhileTheLinkIsPaused() {
    assistant.setStatus(AssistantStatus.INACTIVE);

    assertThatThrownBy(() -> service.listStudents(CALLER, "van-sprinter", OperationShift.MORNING))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(vehicles, passengers);
  }

  private void givenVehicleOf(DriverModel owner, boolean active) {
    mainVehicle.setDriver(owner);
    mainVehicle.setActive(active);
    when(vehicles.findByToken("van-sprinter")).thenReturn(Optional.of(mainVehicle));
  }

  private static RoutePassengerDTO passenger(
      PassengerSource source,
      String token,
      String name,
      RouteLeg leg,
      LocalTime pickup,
      String neighborhood,
      String school) {
    return new RoutePassengerDTO(
        source,
        token,
        name,
        null,
        null,
        school,
        leg,
        pickup,
        null,
        null,
        null,
        null,
        neighborhood,
        null,
        null,
        null);
  }
}
