package br.com.vanep.vehicledocument.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.schooltransportcredential.service.SchoolTransportCredentialService;
import br.com.vanep.shared.document.dto.DocumentDataCorrectionRequestDTO;
import br.com.vanep.shared.document.dto.DocumentStatusUpdateRequestDTO;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.dto.VehicleDocumentRequestDTO;
import br.com.vanep.vehicledocument.dto.VehicleDocumentResponseDTO;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.mapper.VehicleDocumentMapper;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
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
public class VehicleDocumentService {

  private final VehicleDocumentRepository documents;
  private final VehicleRepository vehicles;
  private final DocumentReviewService reviewService;
  private final DocumentSubmissionRules submissionRules;
  private final VehicleDocumentMapper mapper;
  private final MediaFileService media;
  private final MessageSource messages;

  public VehicleDocumentService(
      VehicleDocumentRepository documents,
      VehicleRepository vehicles,
      DocumentReviewService reviewService,
      DocumentSubmissionRules submissionRules,
      VehicleDocumentMapper mapper,
      MediaFileService media,
      MessageSource messages) {
    this.documents = documents;
    this.vehicles = vehicles;
    this.reviewService = reviewService;
    this.submissionRules = submissionRules;
    this.mapper = mapper;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public VehicleDocumentResponseDTO create(String vehicleToken, VehicleDocumentRequestDTO request) {
    VehicleModel vehicle = requireVehicle(vehicleToken);

    VehicleDocumentModel document = new VehicleDocumentModel();
    document.setVehicle(vehicle);
    document.setDocumentType(request.documentType());
    acceptAsNewSubmission(document, document.getDocumentType());
    VehicleDocumentModel saved = saveSubmission(document);
    saved.setFile(media.store(MediaSlot.VEHICLE_DOCUMENT_FILE, saved.getToken(), request.file()));
    return mapper.toResponse(documents.saveAndFlush(saved));
  }

  @Transactional(readOnly = true)
  public Page<VehicleDocumentResponseDTO> findByVehicle(
      String vehicleToken,
      VehicleDocumentType documentType,
      DocumentStatus status,
      Pageable pageable) {
    requireVehicle(vehicleToken);
    return documents
        .findUnsupersededByVehicleToken(vehicleToken, documentType, status, pageable)
        .map(document -> mapper.toResponse(document));
  }

  @Transactional(readOnly = true)
  public VehicleDocumentResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public VehicleDocumentResponseDTO updateStatus(
      String token, DocumentStatusUpdateRequestDTO request, String reviewerUid) {
    VehicleDocumentModel document = requireByToken(token);
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
    return mapper.toResponse(documents.saveAndFlush(document));
  }

  @Transactional
  public VehicleDocumentResponseDTO correctData(
      String token, DocumentDataCorrectionRequestDTO request, String correctorUid) {
    VehicleDocumentModel document = requireByToken(token);
    reviewService.correctData(
        document,
        request,
        document.getDocumentType(),
        SchoolTransportCredentialService.today(),
        correctorUid);
    document.setDocumentNumber(request.documentNumber());
    document.setExpiresAt(request.expiresAt());
    return mapper.toResponse(documents.saveAndFlush(document));
  }

  @Transactional
  public void delete(String token, boolean mayRemoveReviewed) {
    VehicleDocumentModel document = requireByToken(token);
    submissionRules.requireRemovable(document, mayRemoveReviewed);
    documents.delete(document);
  }

  @Transactional
  public VehicleDocumentResponseDTO restore(String token) {
    if (documents.existsDeletedByToken(token)) {
      restoreSubmission(token);
      VehicleDocumentModel restored = requireByToken(token);
      submissionRules.acceptRestored(restored, sameOwnerAndType(restored));
      return mapper.toResponse(restored);
    }
    if (documents.findByToken(token).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("vehicle_document.already_active"));
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("vehicle_document.not_found"));
  }

  VehicleDocumentModel requireByToken(String token) {
    return documents
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("vehicle_document.not_found")));
  }

  void acceptAsNewSubmission(VehicleDocumentModel document, VehicleDocumentType type) {
    List<VehicleDocumentModel> sameOwnerAndType = sameOwnerAndType(document, type);
    submissionRules.requireNoOtherPending(document, sameOwnerAndType);
    submissionRules.supersedeRejected(sameOwnerAndType);
  }

  List<VehicleDocumentModel> sameOwnerAndType(VehicleDocumentModel document) {
    return sameOwnerAndType(document, document.getDocumentType());
  }

  List<VehicleDocumentModel> sameOwnerAndType(
      VehicleDocumentModel document, VehicleDocumentType type) {
    return documents.findByVehicleIdAndDocumentTypeAndSupersededAtIsNull(
        document.getVehicle().getId(), type);
  }

  VehicleDocumentModel saveSubmission(VehicleDocumentModel document) {
    try {
      return documents.saveAndFlush(document);
    } catch (DataIntegrityViolationException violation) {
      throw submissionRules.translate(violation);
    }
  }

  void restoreSubmission(String token) {
    try {
      documents.restoreByToken(token);
    } catch (DataIntegrityViolationException violation) {
      throw submissionRules.translate(violation);
    }
  }

  static void applyApprovalData(
      VehicleDocumentModel document, DocumentStatusUpdateRequestDTO request) {
    document.setDocumentNumber(request.approves() ? request.documentNumber() : null);
    document.setExpiresAt(request.approves() ? request.expiresAt() : null);
  }

  static void clearApprovalData(VehicleDocumentModel document) {
    document.setDocumentNumber(null);
    document.setExpiresAt(null);
  }

  private VehicleModel requireVehicle(String vehicleToken) {
    return vehicles
        .findByToken(vehicleToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("vehicle.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
