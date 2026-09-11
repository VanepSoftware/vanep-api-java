package br.com.vanep.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.dto.TripResponseDTO;
import br.com.vanep.trip.enums.TripStatus;
import br.com.vanep.trip.mapper.TripMapper;
import br.com.vanep.trip.model.TripModel;
import br.com.vanep.trip.repository.TripRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

  private static final String CALLER = "driver-uid";

  @Mock private TripRepository trips;
  @Mock private DriverRepository drivers;
  @Mock private UserService users;
  @Mock private MessageSource messages;

  private TripService service;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    service =
        new TripService(
            trips,
            drivers,
            users,
            new TripMapper(),
            new TripTransitionPolicy(),
            new TripCoherencePolicy(),
            new WorkWindowPolicy(),
            messages);

    UserModel user = new UserModel();
    user.setId(1L);
    user.setType(UserType.DRIVER);

    driver = new DriverModel();
    driver.setId(10L);
    driver.setUser(user);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);

    when(users.requireByTokenAndType(CALLER, UserType.DRIVER)).thenReturn(user);
    when(drivers.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(messages.getMessage(anyString(), any(), any())).thenReturn("mensagem");
    when(trips.save(any(TripModel.class))).thenAnswer(call -> call.getArgument(0));
    when(trips.saveAndFlush(any(TripModel.class))).thenAnswer(call -> call.getArgument(0));
  }

  private TripModel storedTrip(TripStatus status) {
    TripModel trip = new TripModel();
    trip.setDriver(driver);
    trip.setServiceDate(LocalDate.now(TripService.SERVICE_ZONE));
    trip.setShift(Shift.MORNING);
    trip.setStatus(status);
    trip.setToken("trip-token");
    return trip;
  }

  @Test
  void startingCreatesTheDayTripInProgress() {
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.empty());

    TripResponseDTO started = service.startToday(CALLER, Shift.MORNING);

    assertThat(started.status()).isEqualTo(TripStatus.IN_PROGRESS);
    assertThat(started.startedAt()).isNotNull();
  }

  @Test
  void startingAgainKeepsTheFirstStartedAt() {
    TripModel running = storedTrip(TripStatus.IN_PROGRESS);
    Instant original = Instant.parse("2026-09-10T09:00:00Z");
    running.setStartedAt(original);
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.of(running));

    TripResponseDTO again = service.startToday(CALLER, Shift.MORNING);

    assertThat(again.startedAt()).isEqualTo(original);
    verify(trips, never()).save(any(TripModel.class));
  }

  @Test
  void startingOverACompletedRouteIsRejected() {
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.of(storedTrip(TripStatus.COMPLETED)));

    assertThatThrownBy(() -> service.startToday(CALLER, Shift.MORNING))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void losingTheInsertRaceRereadsTheWinningRow() {
    TripModel winner = storedTrip(TripStatus.SCHEDULED);
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(trips.saveAndFlush(any(TripModel.class)))
        .thenThrow(new DataIntegrityViolationException("slot ocupado"));

    TripResponseDTO started = service.startToday(CALLER, Shift.MORNING);

    assertThat(started.token()).isEqualTo(winner.getToken());
    assertThat(started.status()).isEqualTo(TripStatus.IN_PROGRESS);
  }

  @Test
  void finishingWithoutStartingIsRejected() {
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.finishToday(CALLER, Shift.MORNING))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void finishingAgainKeepsTheFirstFinishedAt() {
    TripModel completed = storedTrip(TripStatus.COMPLETED);
    Instant original = Instant.parse("2026-09-10T12:00:00Z");
    completed.setFinishedAt(original);
    when(trips.findByDriverAndServiceDateAndShift(any(), any(), any()))
        .thenReturn(Optional.of(completed));

    assertThat(service.finishToday(CALLER, Shift.MORNING).finishedAt()).isEqualTo(original);
    verify(trips, never()).save(any(TripModel.class));
  }

  @Test
  void aDriverPendingApprovalCannotOperate() {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);

    assertThatThrownBy(() -> service.startToday(CALLER, Shift.MORNING))
        .isInstanceOf(ResponseStatusException.class);
    verify(trips, never()).saveAndFlush(any(TripModel.class));
  }

  @Test
  void todayReturnsEveryShiftOfTheDay() {
    when(trips.findByDriverAndServiceDate(any(), any()))
        .thenReturn(List.of(storedTrip(TripStatus.COMPLETED), storedTrip(TripStatus.IN_PROGRESS)));

    assertThat(service.findToday(CALLER)).hasSize(2);
  }

  @Test
  void serviceDateComesFromTheSaoPauloClock() {
    assertThat(service.today()).isEqualTo(LocalDate.now(TripService.SERVICE_ZONE));
  }
}
