package br.com.vanep.driverdocument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.dto.DriverDocumentRequestDTO;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentDecisionStatus;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.enums.ReviewMethod;
import br.com.vanep.shared.document.model.DocumentReview;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DriverDocumentServiceTest {

  @Mock private DriverDocumentRepository driverDocumentRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserService userService;
  @Mock private DocumentReviewService reviewService;
  @Mock private DriverDocumentMapper mapper;
  @Mock private MediaFileService media;
  @Mock private MessageSource messages;

  @InjectMocks private DriverDocumentService service;

  private DriverModel driver;
  private UserModel user;
  private DriverDocumentRequestDTO request;

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
        new DriverDocumentRequestDTO(
            "driver-token-123",
            DriverDocumentType.CRIMINAL_RECORD,
            new MockMultipartFile("file", "record.pdf", "application/pdf", new byte[] {1}));
  }

  @Test
  void createThrowsNotFoundWhenUserNotFound() {
    when(userService.requireByToken("unknown-uid"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "user.account.not_found"));

    assertThatThrownBy(() -> service.create(request, "unknown-uid"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.getReason()).contains("user.account.not_found");
            });

    verify(driverDocumentRepository, never()).save(any(DriverDocumentModel.class));
  }

  @Test
  void createSavesDocumentSuccessfully() {
    when(userService.requireByToken("user-token-123")).thenReturn(user);
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));

    DriverDocumentModel saved = new DriverDocumentModel();
    saved.setToken("doc-123");
    saved.setDriver(driver);
    saved.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    when(driverDocumentRepository.save(any(DriverDocumentModel.class))).thenReturn(saved);
    MediaFileModel stored = new MediaFileModel();
    when(media.store(MediaSlot.DRIVER_DOCUMENT_FILE, "doc-123", request.file())).thenReturn(stored);

    service.create(request, "user-token-123");

    assertThat(saved.getFile()).isSameAs(stored);
    assertThat(saved.getDocumentNumber()).isNull();
    assertThat(saved.getExpiresAt()).isNull();
  }

  @Test
  void updateStatusUpdatesDocumentSuccessfully() {
    DriverDocumentModel existing = new DriverDocumentModel();
    existing.setToken("doc-123");
    existing.setDriver(driver);

    when(driverDocumentRepository.findByToken("doc-123")).thenReturn(Optional.of(existing));
    when(driverDocumentRepository.save(any(DriverDocumentModel.class))).thenReturn(existing);

    existing.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    DocumentStatusUpdateRequestDTO statusRequest =
        new DocumentStatusUpdateRequestDTO(
            DocumentDecisionStatus.APPROVED,
            ReviewMethod.MANUAL,
            null,
            null,
            " CERT-1 ",
            LocalDate.of(2027, 1, 1));
    DocumentReview approved = new DocumentReview();
    approved.setStatus(DocumentStatus.APPROVED);
    approved.setReviewMethod(ReviewMethod.MANUAL);
    when(reviewService.review(statusRequest, null, DriverDocumentType.CRIMINAL_RECORD, "admin-uid"))
        .thenReturn(approved);

    service.updateStatus("doc-123", statusRequest, "admin-uid");

    verify(driverDocumentRepository).save(existing);
    assertThat(existing.getReview()).isSameAs(approved);
    assertThat(existing.getDocumentNumber()).isEqualTo("CERT-1");
    assertThat(existing.getExpiresAt()).isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  void aRejectionDoesNotStoreTheDataOfItsBody() {
    DriverDocumentModel existing = new DriverDocumentModel();
    existing.setToken("doc-123");
    existing.setDriver(driver);
    existing.setDocumentType(DriverDocumentType.CRIMINAL_RECORD);
    when(driverDocumentRepository.findByToken("doc-123")).thenReturn(Optional.of(existing));
    when(driverDocumentRepository.save(any(DriverDocumentModel.class))).thenReturn(existing);
    DocumentStatusUpdateRequestDTO rejection =
        new DocumentStatusUpdateRequestDTO(
            DocumentDecisionStatus.REJECTED,
            ReviewMethod.MANUAL,
            null,
            "Ilegível",
            "CERT-1",
            LocalDate.of(2027, 1, 1));
    when(reviewService.review(rejection, null, DriverDocumentType.CRIMINAL_RECORD, "admin-uid"))
        .thenReturn(new DocumentReview());

    service.updateStatus("doc-123", rejection, "admin-uid");

    assertThat(existing.getDocumentNumber()).isNull();
    assertThat(existing.getExpiresAt()).isNull();
  }

  @Test
  void restoreThrowsConflictWhenDocumentAlreadyActive() {
    DriverDocumentModel active = new DriverDocumentModel();
    active.setToken("doc-123");

    when(driverDocumentRepository.existsDeletedByToken("doc-123")).thenReturn(false);
    when(driverDocumentRepository.findByToken("doc-123")).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> service.restore("doc-123"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> {
              ResponseStatusException ex = (ResponseStatusException) e;
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.getReason()).contains("driver_document.already_active");
            });

    verify(driverDocumentRepository, never()).restoreByToken(any());
  }

  @Test
  void restoreSuccessfullyWhenDocumentDeleted() {
    when(driverDocumentRepository.existsDeletedByToken("doc-123")).thenReturn(true);

    DriverDocumentModel restored = new DriverDocumentModel();
    restored.setToken("doc-123");
    restored.setDriver(driver);
    when(driverDocumentRepository.findByToken("doc-123")).thenReturn(Optional.of(restored));

    service.restore("doc-123");

    verify(driverDocumentRepository).restoreByToken("doc-123");
  }
}
