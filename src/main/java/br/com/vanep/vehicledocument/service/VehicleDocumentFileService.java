package br.com.vanep.vehicledocument.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.service.DocumentReviewService;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehicleDocumentFileService {

  private final VehicleDocumentService documentService;
  private final VehicleDocumentRepository documents;
  private final DocumentReviewService reviewService;
  private final MediaFileService media;
  private final MessageSource messages;

  public VehicleDocumentFileService(
      VehicleDocumentService documentService,
      VehicleDocumentRepository documents,
      DocumentReviewService reviewService,
      MediaFileService media,
      MessageSource messages) {
    this.documentService = documentService;
    this.documents = documents;
    this.reviewService = reviewService;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    VehicleDocumentModel document = documentService.requireByToken(token);
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
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          messages.getMessage("media.not_found", null, LocaleContextHolder.getLocale()));
    }
    return file;
  }
}
