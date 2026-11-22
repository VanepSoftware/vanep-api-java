package br.com.vanep.drivercnh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.dto.DriverCnhDataDTO;
import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhStatusUpdateRequestDTO;
import br.com.vanep.drivercnh.mapper.DriverCnhMapper;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.model.DocumentDecision;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverCnhServiceTest {

  @Mock private DriverCnhRepository cnhRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private DocumentReviewService reviewService;
  @Mock private DocumentSubmissionRules submissionRules;
  @Mock private DriverCnhMapper mapper;
  @Mock private MediaFileService media;
  @Mock private MessageSource messages;

  @InjectMocks private DriverCnhService service;

  private DriverModel driver;
  private UserModel user;
  private static final LocalDate TODAY = SchoolTransportCredentialService.today();

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
            new MockMultipartFile("file", "cnh.pdf", "application/pdf", new byte[] {1}));
  }

  @Test
  void approvingWithTheNumberOfAnotherDriverIsRefused() {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    when(cnhRepository.existsByRegistrationNumberAndDriverIdNot("12345678901", 10L))
        .thenReturn(true);

    assertThatThrownBy(() -> service.requireApprovalData(cnh, data(), TODAY))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(problem.getMessageKey()).isEqualTo("driver_cnh.registration.duplicate");
            });
  }

  @Test
  void approvingWithoutTheCategoryNamesTheField() {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    DriverCnhDataDTO withoutCategory =
        new DriverCnhDataDTO(
            "12345678901", null, TODAY.minusYears(1), TODAY.plusYears(5), null, null, null);

    assertThatThrownBy(() -> service.requireApprovalData(cnh, withoutCategory, TODAY))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> {
              assertThat(problem.getMessageKey()).isEqualTo("document.review.field_required");
              assertThat(problem.getBody().getProperties()).containsEntry("field", "category");
            });
  }

  @Test
  void aCorrectionMayKeepAnExpiryBeforeToday() {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    DriverCnhDataDTO expired =
        new DriverCnhDataDTO(
            "12345678901", "D", TODAY.minusYears(10), TODAY.minusDays(2), null, null, null);

    service.requireApprovalData(cnh, expired, LocalDate.MIN);
    assertThatThrownBy(() -> service.requireApprovalData(cnh, expired, TODAY))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem -> assertThat(problem.getMessageKey()).isEqualTo("document.expires_at.past"));
  }

  @Test
  void aRenewalIsANewSubmissionNextToTheCurrentCnh() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    DriverCnhModel current = new DriverCnhModel();
    current.setDriver(driver);
    current.setRegistrationNumber("12345678901");
    when(cnhRepository.findByDriverIdAndSupersededAtIsNull(10L)).thenReturn(List.of(current));
    when(cnhRepository.saveAndFlush(any(DriverCnhModel.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MediaFileModel stored = new MediaFileModel();
    when(media.store(eq(MediaSlot.DRIVER_CNH_FILE), any(), eq(request.file()))).thenReturn(stored);

    service.create(request, "driver@vanep.com");

    verify(submissionRules).requireNoOtherPending(any(), eq(List.of(current)));
    verify(submissionRules).supersedeRejected(List.of(current));
    verify(media).store(eq(MediaSlot.DRIVER_CNH_FILE), any(), eq(request.file()));
  }

  @Test
  void aDeadlockBetweenTwoDriversWritingTheSameNumberIsTheNumberConflict() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(cnhRepository.saveAndFlush(any(DriverCnhModel.class)))
        .thenThrow(
            new CannotAcquireLockException(
                "deadlock", new SQLException("deadlock detected", "40P01")));

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey()).isEqualTo("driver_cnh.registration.duplicate"));
  }

  @Test
  void anExclusionViolationIsTheNumberOfAnotherDriver() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    when(cnhRepository.saveAndFlush(any(DriverCnhModel.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "exclusion",
                new SQLException(
                    "conflitando valor de chave viola restrição de exclusão", "23P01")));

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com"))
        .isInstanceOfSatisfying(
            ApiProblemException.class,
            problem ->
                assertThat(problem.getMessageKey()).isEqualTo("driver_cnh.registration.duplicate"));
  }

  @Test
  void anyOtherLockFailureIsKept() {
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));
    CannotAcquireLockException timeout =
        new CannotAcquireLockException("timeout", new SQLException("lock timeout", "55P03"));
    when(cnhRepository.saveAndFlush(any(DriverCnhModel.class))).thenThrow(timeout);

    assertThatThrownBy(() -> service.create(request, "driver@vanep.com")).isSameAs(timeout);
  }

  @Test
  void approvingSupersedesTheOtherCnhsOfTheDriver() {
    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setToken("cnh-123");
    cnh.setDriver(driver);
    DriverCnhStatusUpdateRequestDTO decision =
        new DriverCnhStatusUpdateRequestDTO(
            DocumentDecisionStatus.APPROVED,
            null,
            null,
            null,
            null,
            "12345678901",
            " ad ",
            TODAY.minusYears(1),
            TODAY.plusYears(5),
            null,
            null,
            "DF",
            0L);
    DocumentReview approved = new DocumentReview();
    approved.setStatus(DocumentStatus.APPROVED);
    when(cnhRepository.findByToken("cnh-123")).thenReturn(Optional.of(cnh));
    when(reviewService.review(
            eq(cnh), any(DocumentDecision.class), eq(0L), any(Runnable.class), eq("admin-uid")))
        .thenReturn(approved);
    when(cnhRepository.findByDriverIdAndSupersededAtIsNull(10L)).thenReturn(List.of(cnh));
    when(cnhRepository.saveAndFlush(cnh)).thenReturn(cnh);

    service.updateStatus("cnh-123", decision, "admin-uid");

    assertThat(cnh.getReview()).isSameAs(approved);
    assertThat(cnh.getRegistrationNumber()).isEqualTo("12345678901");
    assertThat(cnh.getCategory()).isEqualTo("AD");
    assertThat(cnh.getValidUntil()).isEqualTo(TODAY.plusYears(5));
    verify(submissionRules).supersedeAllBut(cnh, List.of(cnh));
  }

  @Test
  void restoreThrowsConflictWhenCnhAlreadyActive() {
    DriverCnhModel active = new DriverCnhModel();
    active.setToken("cnh-123");

    when(cnhRepository.existsDeletedByToken("cnh-123")).thenReturn(false);
    when(cnhRepository.findByToken("cnh-123")).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.restore("cnh-123", true))
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

    service.restore("cnh-123", false);

    verify(cnhRepository).restoreByToken("cnh-123");
    verify(submissionRules).requirePending(restored);
    verify(submissionRules).acceptRestored(any(), any());
  }

  private static DriverCnhDataDTO data() {
    return new DriverCnhDataDTO(
        "12345678901", "D", TODAY.minusYears(1), TODAY.plusYears(5), null, null, "DF");
  }
}
