package br.com.vanep.driverdocument.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.dto.DriverDocumentRequestDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.enums.DriverDocumentType;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.dto.DocumentDataCorrectionRequestDTO;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverDocumentService {

  private final DriverDocumentRepository driverDocumentRepository;
  private final DriverRepository driverRepository;
  private final UserService userService;
  private final DocumentReviewService reviewService;
  private final DocumentSubmissionRules submissionRules;
  private final DriverDocumentMapper mapper;
  private final MediaFileService media;
  private final MessageSource messages;

  public DriverDocumentService(
      DriverDocumentRepository driverDocumentRepository,
      DriverRepository driverRepository,
      UserService userService,
      DocumentReviewService reviewService,
      DocumentSubmissionRules submissionRules,
      DriverDocumentMapper mapper,
      MediaFileService media,
      MessageSource messages) {
    this.driverDocumentRepository = driverDocumentRepository;
    this.driverRepository = driverRepository;
    this.userService = userService;
    this.reviewService = reviewService;
    this.submissionRules = submissionRules;
    this.mapper = mapper;
    this.media = media;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DriverDocumentResponseDTO create(DriverDocumentRequestDTO request, String callerUid) {
    UserModel caller = userService.requireByToken(callerUid);

    DriverModel driver;
    if (caller.getType() == UserType.ADMIN
        && request.driverToken() != null
        && !request.driverToken().isBlank()) {
      driver =
          driverRepository
              .findByToken(request.driverToken())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("driver_document.driver.not_found")));
    } else {
      driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
    }

    DriverDocumentModel document = new DriverDocumentModel();
    document.setDriver(driver);
    document.setDocumentType(request.documentType());
    acceptAsNewSubmission(document, document.getDocumentType());
    DriverDocumentModel saved = saveSubmission(document);
    saved.setFile(media.store(MediaSlot.DRIVER_DOCUMENT_FILE, saved.getToken(), request.file()));

    return mapper.toResponse(driverDocumentRepository.saveAndFlush(saved));
  }

  @Transactional(readOnly = true)
  public Page<DriverDocumentResponseDTO> findAll(
      String driverToken,
      DriverDocumentType documentType,
      DocumentStatus status,
      String callerUid,
      Pageable pageable) {
    UserModel caller = userService.requireByToken(callerUid);

    Long targetDriverId = null;

    if (caller.getType() == UserType.ADMIN) {
      if (driverToken != null && !driverToken.isBlank()) {
        DriverModel driver =
            driverRepository
                .findByToken(driverToken)
                .orElseThrow(
                    () ->
                        new ResponseStatusException(
                            HttpStatus.NOT_FOUND, message("driver_document.driver.not_found")));
        targetDriverId = driver.getId();
      }
    } else {
      DriverModel driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
      targetDriverId = driver.getId();
    }

    return driverDocumentRepository
        .findFiltered(targetDriverId, documentType, status, pageable)
        .map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverDocumentResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverDocumentResponseDTO updateStatus(
      String token, DocumentStatusUpdateRequestDTO request, String reviewerUid) {
    DriverDocumentModel document = requireByToken(token);
    document.setReview(
        reviewService.review(
            document,
            request,
            document.getDocumentType(),
            SchoolTransportCredentialService.today(),
            reviewerUid));
    applyApprovalData(document, request);
    if (document.getReview().getStatus() == DocumentStatus.APPROVED) {
      submissionRules.supersedeAllBut(document, sameOwnerAndType(document));
    }

    return mapper.toResponse(driverDocumentRepository.saveAndFlush(document));
  }

  @Transactional
  public DriverDocumentResponseDTO correctData(
      String token, DocumentDataCorrectionRequestDTO request, String correctorUid) {
    DriverDocumentModel document = requireByToken(token);
    reviewService.correctData(
        document,
        request,
        document.getDocumentType(),
        SchoolTransportCredentialService.today(),
        correctorUid);
    document.setDocumentNumber(request.documentNumber());
    document.setExpiresAt(request.expiresAt());
    return mapper.toResponse(driverDocumentRepository.saveAndFlush(document));
  }

  @Transactional
  public void delete(String token, boolean mayRemoveReviewed) {
    DriverDocumentModel document = requireByToken(token);
    submissionRules.requireRemovable(document, mayRemoveReviewed);
    driverRepository.lockById(document.getDriver().getId());
    driverDocumentRepository.delete(document);
  }

  @Transactional
  public DriverDocumentResponseDTO restore(String token) {
    if (driverDocumentRepository.existsDeletedByToken(token)) {
      restoreSubmission(token);
      DriverDocumentModel restored = requireByToken(token);
      submissionRules.acceptRestored(restored, sameOwnerAndType(restored));
      return mapper.toResponse(restored);
    }

    if (driverDocumentRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_document.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_document.not_found"));
  }

  void acceptAsNewSubmission(DriverDocumentModel document, DriverDocumentType type) {
    List<DriverDocumentModel> sameOwnerAndType = sameOwnerAndType(document, type);
    submissionRules.requireNoOtherPending(document, sameOwnerAndType);
    submissionRules.supersedeRejected(sameOwnerAndType);
  }

  List<DriverDocumentModel> sameOwnerAndType(DriverDocumentModel document) {
    return sameOwnerAndType(document, document.getDocumentType());
  }

  List<DriverDocumentModel> sameOwnerAndType(
      DriverDocumentModel document, DriverDocumentType type) {
    return driverDocumentRepository.findByDriverIdAndDocumentTypeAndSupersededAtIsNull(
        document.getDriver().getId(), type);
  }

  DriverDocumentModel saveSubmission(DriverDocumentModel document) {
    try {
      return driverDocumentRepository.saveAndFlush(document);
    } catch (DataIntegrityViolationException violation) {
      throw submissionRules.translate(violation);
    }
  }

  void restoreSubmission(String token) {
    try {
      driverDocumentRepository.restoreByToken(token);
    } catch (DataIntegrityViolationException violation) {
      throw submissionRules.translate(violation);
    }
  }

  static void applyApprovalData(
      DriverDocumentModel document, DocumentStatusUpdateRequestDTO request) {
    document.setDocumentNumber(request.approves() ? request.documentNumber() : null);
    document.setExpiresAt(request.approves() ? request.expiresAt() : null);
  }

  static void clearApprovalData(DriverDocumentModel document) {
    document.setDocumentNumber(null);
    document.setExpiresAt(null);
  }

  DriverDocumentModel requireByToken(String token) {
    return driverDocumentRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("driver_document.not_found")));
  }
}
