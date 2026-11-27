package br.com.vanep.vehicledocument.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehicleDocumentFileService {

  private final VehicleDocumentService documentService;
  private final VehicleDocumentRepository documents;
  private final DocumentReviewService reviewService;
  private final DocumentSubmissionRules submissionRules;
  private final MediaFileService media;

  public VehicleDocumentFileService(
      VehicleDocumentService documentService,
      VehicleDocumentRepository documents,
      DocumentReviewService reviewService,
      DocumentSubmissionRules submissionRules,
      MediaFileService media) {
    this.documentService = documentService;
    this.documents = documents;
    this.reviewService = reviewService;
    this.submissionRules = submissionRules;
    this.media = media;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    VehicleDocumentModel document = documentService.requireByToken(token);
    submissionRules.requirePending(document);
    MediaFileModel previous = document.getFile();

    MediaFileModel stored = media.store(MediaSlot.VEHICLE_DOCUMENT_FILE, document.getToken(), file);
    document.setFile(stored);
    document.setReview(reviewService.clear());
    VehicleDocumentService.clearApprovalData(document);
    documents.save(document);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel requireFile(String token) {
    MediaFileModel file = documentService.requireByToken(token).getFile();
    if (file == null) {
      throw ApiProblemException.notFound("media.not_found");
    }
    return file;
  }
}
