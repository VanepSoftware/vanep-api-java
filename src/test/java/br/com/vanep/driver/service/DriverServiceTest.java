package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.DriverUpdateRequestDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

  @Mock private DriverRepository repository;
  @Mock private DriverMapper mapper;
  @Mock private MessageSource messages;

  private DriverService service;

  @BeforeEach
  void setUp() {
    service = new DriverService(repository, mapper, messages);
  }

  private DriverModel driverWithToken(String token) {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Test Driver");
    user.setEmail("driver@vanep.com");
    user.setDocument("12345678901");
    user.setToken("owner-uid");

    DriverModel driver = new DriverModel();
    driver.setToken(token);
    driver.setUser(user);
    driver.setBasePrice(BigDecimal.valueOf(50));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    return driver;
  }

  @Test
  void findAllReturnsPagedResponses() {
    DriverModel driver = driverWithToken("abc");
    DriverResponseDTO response =
        new DriverResponseDTO(
            "abc", "Test", "t@t.com", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, true, false, null, null);
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(driver)));
    when(mapper.toResponse(driver)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    DriverModel driver = driverWithToken("tok");
    DriverResponseDTO response =
        new DriverResponseDTO(
            "tok", "Test", "t@t.com", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, true, false, null, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));
    when(mapper.toResponse(driver)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Not found");

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updatePersistsFields() {
    DriverModel driver = driverWithToken("tok");
    DriverResponseDTO response =
        new DriverResponseDTO(
            "tok",
            "Test",
            "t@t.com",
            null,
            null,
            "photo.jpg",
            null,
            "bio text",
            "123",
            5,
            "city",
            BigDecimal.valueOf(100),
            LocalTime.of(8, 0),
            LocalTime.of(18, 0),
            List.of("Mon"),
            10,
            List.of("Area"),
            DriverApprovalStatus.APPROVED,
            true,
            true,
            null,
            null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));
    when(repository.save(driver)).thenReturn(driver);
    when(mapper.toResponse(driver)).thenReturn(response);

    DriverUpdateRequestDTO req =
        new DriverUpdateRequestDTO(
            "photo.jpg",
            "bio text",
            "123",
            5,
            "city",
            BigDecimal.valueOf(100),
            LocalTime.of(8, 0),
            LocalTime.of(18, 0),
            List.of("Mon"),
            10,
            List.of("Area"),
            true);
    DriverResponseDTO result = service.update("tok", req);

    assertThat(result).isEqualTo(response);
    assertThat(driver.getPhoto()).isEqualTo("photo.jpg");
    assertThat(driver.getBio()).isEqualTo("bio text");
    assertThat(driver.getCnpj()).isEqualTo("123");
    assertThat(driver.getExperienceYears()).isEqualTo(5);
    assertThat(driver.getCity()).isEqualTo("city");
    assertThat(driver.getBasePrice()).isEqualTo(BigDecimal.valueOf(100));
    assertThat(driver.getWorkStartTime()).isEqualTo(LocalTime.of(8, 0));
    assertThat(driver.getWorkEndTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(driver.getWorkDays()).containsExactly("Mon");
    assertThat(driver.getWaitToleranceMinutes()).isEqualTo(10);
    assertThat(driver.getServiceAreas()).containsExactly("Area");
    assertThat(driver.isAvailable()).isTrue();
  }

  @Test
  void deleteRemovesDriver() {
    DriverModel driver = driverWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));

    service.delete("tok");

    verify(repository).delete(driver);
  }

  @Test
  void restoreRestoresDeletedDriver() {
    DriverModel driver = driverWithToken("tok");
    DriverResponseDTO response =
        new DriverResponseDTO(
            "tok", "Test", "t@t.com", null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, true, false, null, null);
    when(repository.existsDeletedByToken("tok")).thenReturn(true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));
    when(mapper.toResponse(driver)).thenReturn(response);

    DriverResponseDTO result = service.restore("tok");

    verify(repository).restoreByToken("tok");
    assertThat(result).isEqualTo(response);
  }

  @Test
  void restoreThrowsConflictWhenAlreadyActive() {
    DriverModel driver = driverWithToken("tok");
    when(repository.existsDeletedByToken("tok")).thenReturn(false);
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Already active");

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void restoreThrowsNotFoundWhenDriverDoesNotExist() {
    when(repository.existsDeletedByToken("tok")).thenReturn(false);
    when(repository.findByToken("tok")).thenReturn(Optional.empty());
    when(messages.getMessage(anyString(), any(), any())).thenReturn("Not found");

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
