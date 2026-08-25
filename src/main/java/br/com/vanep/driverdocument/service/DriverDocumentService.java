package br.com.vanep.driverdocument.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverdocument.dto.DriverDocumentRequestDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentResponseDTO;
import br.com.vanep.driverdocument.dto.DriverDocumentStatusUpdateRequestDTO;
import br.com.vanep.driverdocument.enums.DocumentStatusEnum;
import br.com.vanep.driverdocument.enums.DocumentTypeEnum;
import br.com.vanep.driverdocument.mapper.DriverDocumentMapper;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import br.com.vanep.user.model.UserModel;
import java.time.Instant;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
  private final UserRepository userRepository;
  private final DriverDocumentMapper mapper;
  private final MessageSource messages;

  public DriverDocumentService(
      DriverDocumentRepository driverDocumentRepository,
      DriverRepository driverRepository,
      UserRepository userRepository,
      DriverDocumentMapper mapper,
      MessageSource messages) {
    this.driverDocumentRepository = driverDocumentRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DriverDocumentResponseDTO create(DriverDocumentRequestDTO request, String callerEmail) {
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
    document.setFileUrl(request.fileUrl());
    document.setExpiresAt(request.expiresAt());
    document.setStatus(DocumentStatusEnum.PENDING);

    return mapper.toResponse(driverDocumentRepository.save(document));
  }

  @Transactional(readOnly = true)
  public Page<DriverDocumentResponseDTO> findAll(
      String driverToken,
      DocumentTypeEnum documentType,
      DocumentStatusEnum status,
      String callerEmail,
      Pageable pageable) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

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

    Page<DriverDocumentModel> documents;
    if (targetDriverId != null) {
      if (documentType != null) {
        documents =
            driverDocumentRepository.findByDriverIdAndDocumentType(
                targetDriverId, documentType, pageable);
      } else if (status != null) {
        documents =
            driverDocumentRepository.findByDriverIdAndStatus(targetDriverId, status, pageable);
      } else {
        documents = driverDocumentRepository.findByDriverId(targetDriverId, pageable);
      }
    } else {
      documents = driverDocumentRepository.findAll(pageable);
    }

    return documents.map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverDocumentResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverDocumentResponseDTO update(String token, DriverDocumentRequestDTO request) {
    DriverDocumentModel document = requireByToken(token);
    document.setDocumentType(request.documentType());
    document.setFileUrl(request.fileUrl());
    document.setExpiresAt(request.expiresAt());
    document.setStatus(DocumentStatusEnum.PENDING);

    return mapper.toResponse(driverDocumentRepository.save(document));
  }

  @Transactional
  public DriverDocumentResponseDTO updateStatus(
      String token, DriverDocumentStatusUpdateRequestDTO request, String callerEmail) {
    DriverDocumentModel document = requireByToken(token);

    UserModel reviewer =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    document.setStatus(request.status());
    document.setReviewMethod(request.reviewMethod());
    document.setExternalCheckId(request.externalCheckId());
    document.setRejectionReason(request.rejectionReason());
    document.setReviewedBy(reviewer);
    document.setReviewedAt(Instant.now());

    return mapper.toResponse(driverDocumentRepository.save(document));
  }

  @Transactional
  public void delete(String token) {
    driverDocumentRepository.delete(requireByToken(token));
  }

  @Transactional
  public DriverDocumentResponseDTO restore(String token) {
    if (driverDocumentRepository.existsDeletedByToken(token)) {
      driverDocumentRepository.restoreByToken(token);
      return mapper.toResponse(requireByToken(token));
    }

    if (driverDocumentRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_document.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_document.not_found"));
  }

  private DriverDocumentModel requireByToken(String token) {
    return driverDocumentRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("driver_document.not_found")));
  }
}
