package br.com.vanep.drivercnh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.mapper.DriverCnhMapper;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverCnhServiceTest {

  @Mock private DriverCnhRepository cnhRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private DriverCnhMapper mapper;
  @Mock private MessageSource messages;

  @InjectMocks private DriverCnhService service;

  private DriverModel driver;
  private UserModel user;
  private DriverCnhRequestDTO request;

  @BeforeEach
  void setUp() {
    lenient()
        .when(messages.getMessage(any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
        new DriverCnhRequestDTO(
            "driver-token-123",
            "12345678901",
            "D",
            LocalDate.of(2020, 1, 15),
            LocalDate.of(2030, 1, 15),
            LocalDate.of(2010, 3, 20),
            "987654321",
            "DF",
            "cnh.jpg");
  }

  @Test
  void createThrowsConflictWhenRegistrationExists() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(cnhRepository.existsByRegistrationNumber("12345678901")).thenReturn(true);

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getReason()).contains("driver_cnh.registration.duplicate");
            });

    verify(cnhRepository, never()).save(any(DriverCnhModel.class));
  }

  @Test
  void createThrowsConflictWhenDriverAlreadyHasCnh() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(cnhRepository.existsByRegistrationNumber("12345678901")).thenReturn(false);
    when(cnhRepository.existsByDriverId(10L)).thenReturn(true);

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getReason()).contains("driver_cnh.driver.duplicate");
            });

    verify(cnhRepository, never()).save(any(DriverCnhModel.class));
  }

  @Test
  void createSavesCnhSuccessfully() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(cnhRepository.existsByRegistrationNumber("12345678901")).thenReturn(false);
    when(cnhRepository.existsByDriverId(10L)).thenReturn(false);

    DriverCnhModel saved = new DriverCnhModel();
    saved.setDriver(driver);
    saved.setRegistrationNumber("12345678901");
    when(cnhRepository.save(any(DriverCnhModel.class))).thenReturn(saved);

    service.create(request, "driver@vanep.com");

    verify(cnhRepository).save(any(DriverCnhModel.class));
  }

  @Test
  void restoreThrowsConflictWhenCnhAlreadyActive() {
    DriverCnhModel active = new DriverCnhModel();
    active.setToken("cnh-123");

    when(cnhRepository.existsDeletedByToken("cnh-123")).thenReturn(false);
    when(cnhRepository.findByToken("cnh-123")).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.restore("cnh-123"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getReason()).contains("driver_cnh.already_active");
            });

    verify(cnhRepository, never()).restoreByToken(any());
  }

  @Test
  void restoreSuccessfullyWhenCnhDeleted() {
    when(cnhRepository.existsDeletedByToken("cnh-123")).thenReturn(true);

    DriverCnhModel restored = new DriverCnhModel();
    restored.setToken("cnh-123");
    restored.setDriver(driver);
    when(cnhRepository.findByToken("cnh-123")).thenReturn(Optional.of(restored));

    service.restore("cnh-123");

    verify(cnhRepository).restoreByToken("cnh-123");
  }
}
