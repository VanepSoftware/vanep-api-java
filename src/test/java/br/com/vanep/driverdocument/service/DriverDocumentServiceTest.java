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
import br.com.vanep.driverdocument.dto.DriverDocumentStatusUpdateRequestDTO;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.enums.ReviewMethodEnum;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
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
class DriverDocumentServiceTest {

  @Mock private DriverDocumentRepository driverDocumentRepository;
  @Mock private DriverRepository driverRepository;
  @Mock private UserRepository userRepository;
  @Mock private DriverDocumentMapper mapper;
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
            DocumentTypeEnum.CRLV,
            "https://storage.vanep.com.br/crlv.pdf",
            LocalDate.of(2027, 1, 1));
  }

  @Test
  void createThrowsNotFoundWhenUserNotFound() {
    when(userRepository.findByEmail("unknown@vanep.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(request, "unknown@vanep.com"))
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
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverRepository.findByUserId(1L)).thenReturn(Optional.of(driver));

    DriverDocumentModel saved = new DriverDocumentModel();
    saved.setDriver(driver);
    saved.setDocumentType(DocumentTypeEnum.CRLV);
    saved.setFileUrl("https://storage.vanep.com.br/crlv.pdf");
    when(driverDocumentRepository.save(any(DriverDocumentModel.class))).thenReturn(saved);

    service.create(request, "driver@vanep.com");

    verify(driverDocumentRepository).save(any(DriverDocumentModel.class));
  }

  @Test
  void updateStatusUpdatesDocumentSuccessfully() {
    DriverDocumentModel existing = new DriverDocumentModel();
    existing.setToken("doc-123");
    existing.setDriver(driver);

    when(driverDocumentRepository.findByToken("doc-123")).thenReturn(Optional.of(existing));
    when(userRepository.findByEmail("driver@vanep.com")).thenReturn(Optional.of(user));
    when(driverDocumentRepository.save(any(DriverDocumentModel.class))).thenReturn(existing);

    DriverDocumentStatusUpdateRequestDTO statusRequest =
        new DriverDocumentStatusUpdateRequestDTO(
            DocumentStatusEnum.APPROVED, ReviewMethodEnum.MANUAL, null, null);

    service.updateStatus("doc-123", statusRequest, "driver@vanep.com");

    verify(driverDocumentRepository).save(existing);
    assertThat(existing.getStatus()).isEqualTo(DocumentStatusEnum.APPROVED);
    assertThat(existing.getReviewMethod()).isEqualTo(ReviewMethodEnum.MANUAL);
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
