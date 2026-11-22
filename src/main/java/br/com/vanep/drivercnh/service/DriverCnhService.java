package br.com.vanep.drivercnh.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.dto.DriverCnhDataCorrectionRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhDataDTO;
import br.com.vanep.drivercnh.dto.DriverCnhRequestDTO;
import br.com.vanep.drivercnh.dto.DriverCnhResponseDTO;
import br.com.vanep.drivercnh.dto.DriverCnhStatusUpdateRequestDTO;
import br.com.vanep.drivercnh.mapper.DriverCnhMapper;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.service.DocumentDatesPolicy;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverCnhService {

  // The longest validity the CTB gives a CNH (art. 147).
  static final int LONGEST_VALIDITY_YEARS = 10;

  static final DriverCnhDataDTO NO_DATA =
      new DriverCnhDataDTO(null, null, null, null, null, null, null);

  private static final String DEADLOCK_SQL_STATE = "40P01";
  // driver_cnh has one exclusion constraint: a registration number belongs to one driver.
  private static final String EXCLUSION_VIOLATION_SQL_STATE = "23P01";

  private final DriverCnhRepository cnhRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;
  private final DocumentReviewService reviewService;
  private final DocumentSubmissionRules submissionRules;
  private final DriverCnhMapper mapper;
  private final MediaFileService media;
  private final MessageSource messages;

  public DriverCnhService(
      DriverCnhRepository cnhRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      DocumentReviewService reviewService,
      DocumentSubmissionRules submissionRules,
      DriverCnhMapper mapper,
      MediaFileService media,
      MessageSource messages) {
    this.cnhRepository = cnhRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
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
  public DriverCnhResponseDTO create(DriverCnhRequestDTO request, String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

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
                          HttpStatus.NOT_FOUND, message("driver_cnh.driver.not_found")));
    } else {
      driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
    }

    DriverCnhModel cnh = new DriverCnhModel();
    cnh.setDriver(driver);
    List<DriverCnhModel> sameDriver = sameDriver(cnh);
    submissionRules.requireNoOtherPending(cnh, sameDriver);
    submissionRules.supersedeRejected(sameDriver);
    DriverCnhModel saved = saveSubmission(cnh);
    saved.setFile(media.store(MediaSlot.DRIVER_CNH_FILE, saved.getToken(), request.file()));

    return mapper.toResponse(cnhRepository.saveAndFlush(saved));
  }

  public List<DriverCnhResponseDTO> findAll(String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    List<DriverCnhModel> cnhs;
    if (caller.getType() == UserType.ADMIN) {
      cnhs = cnhRepository.findBySupersededAtIsNull();
    } else {
      DriverModel driver =
          driverRepository
              .findByUserId(caller.getId())
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
      cnhs = cnhRepository.findByDriverIdAndSupersededAtIsNull(driver.getId());
    }
    return cnhs.stream().map(mapper::toResponse).toList();
  }

  public DriverCnhResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverCnhResponseDTO updateStatus(
      String token, DriverCnhStatusUpdateRequestDTO request, String reviewerUid) {
    DriverCnhModel cnh = requireByToken(token);
    // Read before the data changes: a query after them would flush them outside saveSubmission,
    // and a number taken meanwhile by another driver would not become the 409.
    List<DriverCnhModel> sameDriver = sameDriver(cnh);
    LocalDate today = SchoolTransportCredentialService.today();
    cnh.setReview(
        reviewService.review(
            cnh,
            request.toDecision(),
            request.version(),
            () -> requireApprovalData(cnh, request.data(), today),
            reviewerUid));
    boolean approved = cnh.getReview().getStatus() == DocumentStatus.APPROVED;
    applyData(cnh, approved ? request.data() : NO_DATA);
    if (approved) {
      submissionRules.supersedeAllBut(cnh, sameDriver);
    }
    return mapper.toResponse(saveSubmission(cnh));
  }

  @Transactional
  public DriverCnhResponseDTO correctData(
      String token, DriverCnhDataCorrectionRequestDTO request, String correctorUid) {
    DriverCnhModel cnh = requireByToken(token);
    reviewService.requireCorrectable(cnh, request.version());
    requireApprovalData(cnh, request.data(), LocalDate.MIN);
    reviewService.markCorrected(cnh, correctorUid);
    applyData(cnh, request.data());
    return mapper.toResponse(saveSubmission(cnh));
  }

  // earliestValidity is today on an approval, and open on a correction: a mistyped expiry of a
  // license that has since expired is corrected to its real, past date.
  void requireApprovalData(DriverCnhModel cnh, DriverCnhDataDTO data, LocalDate earliestValidity) {
    requireField("registrationNumber", data.registrationNumber());
    requireField("category", data.category());
    requireField("issueDate", data.issueDate());
    requireField("validUntil", data.validUntil());
    validateDates(data, earliestValidity);
    requireNumberOfNoOtherDriver(data.registrationNumber(), cnh.getDriver());
  }

  static void requireField(String field, Object value) {
    if (value == null) {
      throw DocumentReviewService.fieldRequired(field);
    }
  }

  @Transactional
  public void delete(String token, boolean mayRemoveReviewed) {
    DriverCnhModel cnh = requireByToken(token);
    submissionRules.requireRemovable(cnh, mayRemoveReviewed);
    driverRepository.lockById(cnh.getDriver().getId());
    cnhRepository.delete(cnh);
  }

  @Transactional
  public DriverCnhResponseDTO restore(String token, boolean mayRestoreReviewed) {
    if (cnhRepository.existsDeletedByToken(token)) {
      restoreSubmission(token);
      DriverCnhModel restored = requireByToken(token);
      if (!mayRestoreReviewed) {
        submissionRules.requirePending(restored);
      }
      submissionRules.acceptRestored(restored, sameDriver(restored));
      return mapper.toResponse(restored);
    }

    if (cnhRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("driver_cnh.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_cnh.not_found"));
  }

  void requireNumberOfNoOtherDriver(String registrationNumber, DriverModel driver) {
    if (cnhRepository.existsByRegistrationNumberAndDriverIdNot(
        registrationNumber, driver.getId())) {
      throw registrationOfAnotherDriver();
    }
  }

  List<DriverCnhModel> sameDriver(DriverCnhModel cnh) {
    return cnhRepository.findByDriverIdAndSupersededAtIsNull(cnh.getDriver().getId());
  }

  DriverCnhModel saveSubmission(DriverCnhModel cnh) {
    try {
      return cnhRepository.saveAndFlush(cnh);
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation);
    } catch (CannotAcquireLockException failure) {
      throw translateDeadlock(failure);
    }
  }

  void restoreSubmission(String token) {
    try {
      cnhRepository.restoreByToken(token);
    } catch (DataIntegrityViolationException violation) {
      throw translate(violation);
    } catch (CannotAcquireLockException failure) {
      throw translateDeadlock(failure);
    }
  }

  RuntimeException translate(DataIntegrityViolationException violation) {
    if (hasSqlState(violation, EXCLUSION_VIOLATION_SQL_STATE)) {
      return registrationOfAnotherDriver();
    }
    return submissionRules.translate(violation);
  }

  // Two drivers writing the same new number make the exclusion constraint wait on each other, and
  // PostgreSQL aborts one of them with a deadlock instead of a violation: that one lost the number.
  static RuntimeException translateDeadlock(CannotAcquireLockException failure) {
    return hasSqlState(failure, DEADLOCK_SQL_STATE) ? registrationOfAnotherDriver() : failure;
  }

  static boolean hasSqlState(Throwable failure, String sqlState) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }

  static ApiProblemException registrationOfAnotherDriver() {
    return ApiProblemException.conflict("driver_cnh.registration.duplicate");
  }

  static void validateDates(DriverCnhDataDTO data, LocalDate earliestValidity) {
    if (data.issueDate().isAfter(SchoolTransportCredentialService.today())) {
      throw ApiProblemException.badRequest("driver_cnh.issue_date.future")
          .withProperty("field", "issueDate");
    }
    DocumentDatesPolicy.requireExpiryWithin(
        "validUntil",
        data.validUntil(),
        data.issueDate().plusYears(LONGEST_VALIDITY_YEARS),
        earliestValidity);
  }

  static void applyData(DriverCnhModel cnh, DriverCnhDataDTO data) {
    cnh.setRegistrationNumber(data.registrationNumber());
    cnh.setCategory(data.category());
    cnh.setIssueDate(data.issueDate());
    cnh.setValidUntil(data.validUntil());
    cnh.setFirstLicenseDate(data.firstLicenseDate());
    cnh.setSecurityNumber(data.securityNumber());
    cnh.setIssuingState(data.issuingState());
  }

  private DriverCnhModel requireByToken(String token) {
    return cnhRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_cnh.not_found")));
  }
}
