package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverAvailabilityRequestDTO;
import br.com.vanep.driver.dto.DriverMeSummaryResponseDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.dto.DriverUpdateRequestDTO;
import br.com.vanep.driver.mapper.DriverMapper;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {
  @Mock private DriverRepository repository;
  @Mock private DriverMapper mapper;
  @Mock private UserService userService;
  @Mock private DriverRatingRepository ratings;

  private DriverService service;

  @BeforeEach
  void setUp() {
    service = new DriverService(repository, mapper, userService, ratings);
  }

  private DriverModel driverWithToken(String token) {
    UserModel user = new UserModel();
    user.setId(1L);
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
            null, null, null, true, false, null, null);
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
            null, null, null, true, false, null, null);
    when(repository.findByToken("tok")).thenReturn(Optional.of(driver));
    when(mapper.toResponse(driver)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("driver.not_found");
            });
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
            BigDecimal.valueOf(100),
            LocalTime.of(8, 0),
            LocalTime.of(18, 0),
            List.of("Mon"),
            10,
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
            "bio text",
            "123",
            5,
            BigDecimal.valueOf(100),
            LocalTime.of(8, 0),
            LocalTime.of(18, 0),
            List.of("Mon"),
            10,
            true);
    DriverResponseDTO result = service.update("tok", req);

    assertThat(result).isEqualTo(response);
    assertThat(driver.getBio()).isEqualTo("bio text");
    assertThat(driver.getCnpj()).isEqualTo("123");
    assertThat(driver.getExperienceYears()).isEqualTo(5);
    assertThat(driver.getBasePrice()).isEqualTo(BigDecimal.valueOf(100));
    assertThat(driver.getWorkStartTime()).isEqualTo(LocalTime.of(8, 0));
    assertThat(driver.getWorkEndTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(driver.getWorkDays()).containsExactly("Mon");
    assertThat(driver.getWaitToleranceMinutes()).isEqualTo(10);
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
            null, null, null, true, false, null, null);
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

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getCode()).isEqualTo("driver.already_active");
            });
  }

  @Test
  void restoreThrowsNotFoundWhenDriverDoesNotExist() {
    when(repository.existsDeletedByToken("tok")).thenReturn(false);
    when(repository.findByToken("tok")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("driver.not_found");
            });
  }

  @Test
  void updateMyAvailabilitySetsTheFlagAndReturnsTheSummary() {
    DriverModel driver = driverWithToken("tok");
    UserModel user = driver.getUser();
    UserMeResponseDTO me = null;
    DriverMeSummaryResponseDTO summary =
        new DriverMeSummaryResponseDTO(
            "tok", null, null, 3, DriverApprovalStatus.APPROVED, true, true, BigDecimal.TEN, 8, me);
    when(userService.requireByTokenAndType("owner-uid", UserType.DRIVER)).thenReturn(user);
    when(repository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(repository.save(driver)).thenReturn(driver);
    when(userService.toMeResponse(user)).thenReturn(me);
    when(ratings.countByDriverId(driver.getId())).thenReturn(3L);
    when(mapper.toMeSummary(driver, 3L, me)).thenReturn(summary);

    var result = service.updateMyAvailability("owner-uid", new DriverAvailabilityRequestDTO(true));

    assertThat(driver.isAvailable()).isTrue();
    assertThat(result).isEqualTo(summary);
    verify(repository).save(driver);
  }

  @Test
  void updateMyAvailabilityRejectsACallerWithoutDriverProfile() {
    UserModel user = driverWithToken("tok").getUser();
    when(userService.requireByTokenAndType("owner-uid", UserType.DRIVER)).thenReturn(user);
    when(repository.findByUserId(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.updateMyAvailability("owner-uid", new DriverAvailabilityRequestDTO(false)))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getCode()).isEqualTo("user.driver_profile.not_found");
            });
  }

  @Test
  void requireCallerDriverResolvesTheDriverOfTheCaller() {
    DriverModel driver = driverWithToken("tok");
    when(userService.requireByTokenAndType("owner-uid", UserType.DRIVER))
        .thenReturn(driver.getUser());
    when(repository.findByUserId(1L)).thenReturn(Optional.of(driver));

    assertThat(service.requireCallerDriver("owner-uid")).isSameAs(driver);
  }
}
