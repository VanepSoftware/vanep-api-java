package br.com.vanep.stopchange.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StopChangeRequestServiceTest {

  @Mock private StopChangeRequestRepository repository;
  @Mock private DependentRepository dependentRepository;
  @Mock private TripRepository tripRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private AddressPlaceResolverService placeResolver;
  @Spy private StopChangeTransitionPolicy transitionPolicy = new StopChangeTransitionPolicy();
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private MessageSource messages;

  @InjectMocks private StopChangeRequestService service;

  private UserModel clientUser;
  private ClientModel client;
  private DependentModel dependent;
  private UserModel driverUser;
  private DriverModel driver;
  private TripModel trip;
  private AddressModel address;

  @BeforeEach
  void setUp() {
    clientUser = new UserModel();
    clientUser.setId(1L);
    clientUser.setToken("client-user-tok");
    clientUser.setType(UserType.CLIENT);

    client = new ClientModel();
    client.setId(10L);
    client.setUser(clientUser);

    dependent = new DependentModel();
    dependent.setId(100L);
    dependent.setToken("dep-tok");
    dependent.setClientId(10L);

    driverUser = new UserModel();
    driverUser.setId(2L);
    driverUser.setToken("driver-user-tok");
    driverUser.setType(UserType.DRIVER);

    driver = new DriverModel();
    driver.setId(20L);
    driver.setUser(driverUser);
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);

    trip = new TripModel();
    trip.setId(200L);
    trip.setToken("trip-tok");
    trip.setDriver(driver);
    trip.setStatus(TripStatus.IN_PROGRESS);
    trip.setServiceDate(LocalDate.of(2026, 9, 10));

    address = new AddressModel();
    address.setId(500L);
    address.setToken("addr-tok");
  }

  @Test
  void requestChangeCreatesPendingRequestAndPublishesEvent() {
    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(clientRepository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(dependentRepository.findByToken("dep-tok")).thenReturn(Optional.of(dependent));
    when(tripRepository.findByToken("trip-tok")).thenReturn(Optional.of(trip));
    when(addressRepository.findByToken("addr-tok")).thenReturn(Optional.of(address));
    when(repository.findByDependentIdAndTripIdAndStatus(100L, 200L, StopChangeStatus.PENDING))
        .thenReturn(Optional.empty());
    when(repository.save(any(StopChangeRequestModel.class))).thenAnswer(inv -> inv.getArgument(0));

    StopChangeCreateRequestDTO dto =
        new StopChangeCreateRequestDTO(
            "dep-tok", "trip-tok", "addr-tok", null, null, null, null, "motivo");

    StopChangeRequestModel result = service.requestChange("client-user-tok", dto);

    assertThat(result.getStatus()).isEqualTo(StopChangeStatus.PENDING);
    assertThat(result.getRequestedByUser()).isEqualTo(clientUser);
    assertThat(result.getDependent()).isEqualTo(dependent);
    assertThat(result.getTrip()).isEqualTo(trip);
    assertThat(result.getNewDropoffAddress()).isEqualTo(address);

    verify(eventPublisher).publishEvent(any(StopChangeRequestedEvent.class));
  }

  @Test
  void requestChangeRejectsDependentOwnedByAnotherClient() {
    dependent.setClientId(999L); // different client
    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(clientRepository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(dependentRepository.findByToken("dep-tok")).thenReturn(Optional.of(dependent));

    StopChangeCreateRequestDTO dto =
        new StopChangeCreateRequestDTO(
            "dep-tok", "trip-tok", "addr-tok", null, null, null, null, "motivo");

    assertThatThrownBy(() -> service.requestChange("client-user-tok", dto))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void requestChangeRejectsCompletedTrip() {
    trip.setStatus(TripStatus.COMPLETED);
    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(clientRepository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(dependentRepository.findByToken("dep-tok")).thenReturn(Optional.of(dependent));
    when(tripRepository.findByToken("trip-tok")).thenReturn(Optional.of(trip));

    StopChangeCreateRequestDTO dto =
        new StopChangeCreateRequestDTO(
            "dep-tok", "trip-tok", "addr-tok", null, null, null, null, "motivo");

    assertThatThrownBy(() -> service.requestChange("client-user-tok", dto))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }

  @Test
  void requestChangeRejectsAlreadyPendingRequest() {
    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(clientRepository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(dependentRepository.findByToken("dep-tok")).thenReturn(Optional.of(dependent));
    when(tripRepository.findByToken("trip-tok")).thenReturn(Optional.of(trip));
    when(repository.findByDependentIdAndTripIdAndStatus(100L, 200L, StopChangeStatus.PENDING))
        .thenReturn(Optional.of(new StopChangeRequestModel()));

    StopChangeCreateRequestDTO dto =
        new StopChangeCreateRequestDTO(
            "dep-tok", "trip-tok", "addr-tok", null, null, null, null, "motivo");

    assertThatThrownBy(() -> service.requestChange("client-user-tok", dto))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }

  @Test
  void requestChangeHandlesConcurrentDataIntegrityViolation() {
    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(clientRepository.findByUserId(1L)).thenReturn(Optional.of(client));
    when(dependentRepository.findByToken("dep-tok")).thenReturn(Optional.of(dependent));
    when(tripRepository.findByToken("trip-tok")).thenReturn(Optional.of(trip));
    when(addressRepository.findByToken("addr-tok")).thenReturn(Optional.of(address));
    when(repository.findByDependentIdAndTripIdAndStatus(100L, 200L, StopChangeStatus.PENDING))
        .thenReturn(Optional.empty());
    when(repository.save(any(StopChangeRequestModel.class)))
        .thenThrow(new DataIntegrityViolationException("unique constraint violated"));

    StopChangeCreateRequestDTO dto =
        new StopChangeCreateRequestDTO(
            "dep-tok", "trip-tok", "addr-tok", null, null, null, null, "motivo");

    assertThatThrownBy(() -> service.requestChange("client-user-tok", dto))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }

  @Test
  void approveChangeUpdatesStatusAndPublishesEvent() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.PENDING);
    req.setTrip(trip);
    req.setDependent(dependent);

    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));
    when(repository.save(any(StopChangeRequestModel.class))).thenAnswer(inv -> inv.getArgument(0));

    StopChangeRequestModel result = service.approveChange("driver-user-tok", "req-tok");

    assertThat(result.getStatus()).isEqualTo(StopChangeStatus.APPROVED);
    assertThat(result.getRespondedByUser()).isEqualTo(driverUser);
    assertThat(result.getRespondedAt()).isNotNull();

    verify(eventPublisher).publishEvent(any(StopChangeApprovedEvent.class));
  }

  @Test
  void approveChangeRejectsPendingDriver() {
    driver.setApprovalStatus(DriverApprovalStatus.PENDING);
    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));

    assertThatThrownBy(() -> service.approveChange("driver-user-tok", "req-tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void approveChangeRejectsDriverNotAssignedToTrip() {
    DriverModel otherDriver = new DriverModel();
    otherDriver.setId(999L);
    trip.setDriver(otherDriver);

    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.PENDING);
    req.setTrip(trip);

    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.approveChange("driver-user-tok", "req-tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void approveChangeIsIdempotentWhenAlreadyApproved() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.APPROVED);
    req.setTrip(trip);

    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));

    StopChangeRequestModel result = service.approveChange("driver-user-tok", "req-tok");

    assertThat(result.getStatus()).isEqualTo(StopChangeStatus.APPROVED);
  }

  @Test
  void approveChangeRejectsWhenAlreadyRejected() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.REJECTED);
    req.setTrip(trip);

    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.approveChange("driver-user-tok", "req-tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }

  @Test
  void rejectChangeUpdatesStatusAndPublishesEvent() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.PENDING);
    req.setTrip(trip);
    req.setDependent(dependent);

    when(userRepository.findByToken("driver-user-tok")).thenReturn(Optional.of(driverUser));
    when(driverRepository.findByUserId(2L)).thenReturn(Optional.of(driver));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));
    when(repository.save(any(StopChangeRequestModel.class))).thenAnswer(inv -> inv.getArgument(0));

    StopChangeRequestModel result = service.rejectChange("driver-user-tok", "req-tok");

    assertThat(result.getStatus()).isEqualTo(StopChangeStatus.REJECTED);
    assertThat(result.getRespondedByUser()).isEqualTo(driverUser);
    assertThat(result.getRespondedAt()).isNotNull();

    verify(eventPublisher).publishEvent(any(StopChangeRejectedEvent.class));
  }

  @Test
  void cancelChangeCancelsPendingRequest() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.PENDING);
    req.setRequestedByUser(clientUser);

    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));
    when(repository.save(any(StopChangeRequestModel.class))).thenAnswer(inv -> inv.getArgument(0));

    StopChangeRequestModel result = service.cancelChange("client-user-tok", "req-tok");

    assertThat(result.getStatus()).isEqualTo(StopChangeStatus.CANCELLED);
  }

  @Test
  void cancelChangeRejectsNonRequester() {
    UserModel otherUser = new UserModel();
    otherUser.setId(99L);

    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.PENDING);
    req.setRequestedByUser(otherUser);

    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.cancelChange("client-user-tok", "req-tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void cancelChangeRejectsAlreadyApprovedRequest() {
    StopChangeRequestModel req = new StopChangeRequestModel();
    req.setToken("req-tok");
    req.setStatus(StopChangeStatus.APPROVED);
    req.setRequestedByUser(clientUser);

    when(userRepository.findByToken("client-user-tok")).thenReturn(Optional.of(clientUser));
    when(repository.findByToken("req-tok")).thenReturn(Optional.of(req));

    assertThatThrownBy(() -> service.cancelChange("client-user-tok", "req-tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409 CONFLICT");
  }
}
