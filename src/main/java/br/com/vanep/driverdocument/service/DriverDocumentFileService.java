package br.com.vanep.driverdocument.service;

import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverDocumentFileService {

  private final DriverDocumentRepository documents;
  private final MediaFileService media;
  private final MessageSource messages;

  public DriverDocumentFileService(
      DriverDocumentRepository documents, MediaFileService media, MessageSource messages) {
    this.documents = documents;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    DriverDocumentModel document = require(token);
    MediaFileModel previous = document.getFile();

    MediaFileModel stored = media.store(MediaSlot.DRIVER_DOCUMENT_FILE, document.getToken(), file);
    document.setFile(stored);
    documents.save(document);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel requireFile(String token) {
    MediaFileModel file = require(token).getFile();
    if (file == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("media.not_found"));
    }
    return file;
  }

  private DriverDocumentModel require(String token) {
    return documents
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("driver_document.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
