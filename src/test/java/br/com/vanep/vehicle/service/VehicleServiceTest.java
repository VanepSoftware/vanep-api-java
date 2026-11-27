package br.com.vanep.vehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.vehicle.dto.VehicleRequestDTO;
import br.com.vanep.vehicle.mapper.VehicleMapper;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

  @Mock private VehicleRepository vehicleRepository;
  @Mock private UnlinkedPassengerRepository unlinkedPassengers;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private VehicleMapper mapper;

  @InjectMocks private VehicleService service;

  private DriverModel driver;
  private UserModel user;
  private VehicleRequestDTO request;

  @BeforeEach
  void setUp() {
    user = new UserModel();
    user.setId(1L);
    user.setType(UserType.DRIVER);
    user.setEmail("driver@vanep.com");
    user.setToken("user-token-123");

    driver = new DriverModel();
    driver.setId(10L);
    driver.setUser(user);
    driver.setToken("driver-token-123");

    request =
        new VehicleRequestDTO("driver-token-123", "ABC1D23", "Ford", "Transit", 2022, "White", 15);
  }

  @Test
  void createThrowsConflictWhenPlateExists() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(vehicleRepository.existsByPlate("ABC1D23")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getMessageKey()).isEqualTo("vehicle.plate.duplicate");
            });

    verify(vehicleRepository, never()).save(any(VehicleModel.class));
  }

  @Test
  void aPlateWithAHyphenIsStoredWithoutIt() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(vehicleRepository.save(any(VehicleModel.class))).thenAnswer(inv -> inv.getArgument(0));
    VehicleRequestDTO hyphenated =
        new VehicleRequestDTO("driver-token-123", "ABC-1D23", "Ford", "Transit", 2022, "White", 15);

    service.create(hyphenated, "driver@vanep.com");

    verify(vehicleRepository).existsByPlate("ABC1D23");
    verify(vehicleRepository).save(argThat(vehicle -> "ABC1D23".equals(vehicle.getPlate())));
  }

  @Test
  void theSamePlateWithoutTheHyphenIsADuplicate() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(vehicleRepository.existsByPlate("ABC1D23")).thenReturn(true);
    VehicleRequestDTO hyphenated =
        new VehicleRequestDTO("driver-token-123", "ABC-1D23", "Ford", "Transit", 2022, "White", 15);

    assertThatThrownBy(() -> service.create(hyphenated, "driver@vanep.com"))
        .isInstanceOf(ApiProblemException.class);
  }

  @Test
  void normalizingRemovesHyphensAndSpacesAndUppercases() {
    assertThat(VehicleService.normalizePlate(" abc-1d23 ")).isEqualTo("ABC1D23");
    assertThat(VehicleService.normalizePlate(null)).isNull();
  }

  @Test
  void createSavesVehicleSuccessfully() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(vehicleRepository.existsByPlate("ABC1D23")).thenReturn(false);

    VehicleModel savedVehicle = new VehicleModel();
    savedVehicle.setDriver(driver);
    savedVehicle.setPlate("ABC1D23");

    when(vehicleRepository.save(any(VehicleModel.class))).thenReturn(savedVehicle);

    service.create(request, "driver@vanep.com");

    verify(vehicleRepository).save(any(VehicleModel.class));
  }

  @Test
  void restoreThrowsConflictWhenVehicleAlreadyActive() {
    VehicleModel activeVehicle = new VehicleModel();
    activeVehicle.setToken("veh-123");

    when(vehicleRepository.existsDeletedByToken("veh-123")).thenReturn(false);
    when(vehicleRepository.findByToken("veh-123")).thenReturn(Optional.of(activeVehicle));

    assertThatThrownBy(() -> service.restore("veh-123"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getCode()).isEqualTo("vehicle.already_active");
            });

    verify(vehicleRepository, never()).restoreByToken(any());
  }

  @Test
  void restoreSuccessfullyWhenVehicleDeleted() {
    when(vehicleRepository.existsDeletedByToken("veh-123")).thenReturn(true);

    VehicleModel restored = new VehicleModel();
    restored.setToken("veh-123");
    restored.setPlate("ABC1D23");
    restored.setDriver(driver);

    when(vehicleRepository.findByToken("veh-123")).thenReturn(Optional.of(restored));

    service.restore("veh-123");

    verify(vehicleRepository).restoreByToken("veh-123");
  }

  @Test
  void deleteReleasesStudentsStillPointingAtTheVan() {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setId(30L);
    vehicle.setToken("veh-123");
    vehicle.setDriver(driver);
    when(vehicleRepository.findByToken("veh-123")).thenReturn(Optional.of(vehicle));

    service.delete("veh-123");

    verify(driverRepository).lockById(driver.getId());
    verify(unlinkedPassengers).clearVehicleId(30L);
    verify(vehicleRepository).delete(vehicle);
  }
}
