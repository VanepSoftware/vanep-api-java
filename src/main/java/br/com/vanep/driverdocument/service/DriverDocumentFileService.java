package br.com.vanep.driverdocument.service;

import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.service.DocumentReviewService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverDocumentFileService {

  private final DriverDocumentService documentService;
  private final DriverDocumentRepository documents;
  private final MediaFileService media;
  private final DocumentReviewService reviewService;
  private final MessageSource messages;

  public DriverDocumentFileService(
      DriverDocumentService documentService,
      DriverDocumentRepository documents,
      MediaFileService media,
      DocumentReviewService reviewService,
      MessageSource messages) {
    this.documentService = documentService;
    this.documents = documents;
    this.media = media;
    this.reviewService = reviewService;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    DriverDocumentModel document = documentService.requireByToken(token);
    MediaFileModel previous = document.getFile();

    MediaFileModel stored = media.store(MediaSlot.DRIVER_DOCUMENT_FILE, document.getToken(), file);
    document.setFile(stored);
    document.setReview(reviewService.clear());
    DriverDocumentService.clearApprovalData(document);
    documents.save(document);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel requireFile(String token) {
    MediaFileModel file = documentService.requireByToken(token).getFile();
    if (file == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("media.not_found"));
    }
    return file;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
