package br.com.vanep.drivercnh.service;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.document.service.DocumentSubmissionRules;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriverCnhFileService {

  private final DriverCnhRepository cnhs;
  private final MediaFileService media;
  private final DocumentSubmissionRules submissionRules;
  private final MessageSource messages;

  public DriverCnhFileService(
      DriverCnhRepository cnhs,
      MediaFileService media,
      DocumentSubmissionRules submissionRules,
      MessageSource messages) {
    this.cnhs = cnhs;
    this.media = media;
    this.submissionRules = submissionRules;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    DriverCnhModel cnh = require(token);
    submissionRules.requirePending(cnh);
    MediaFileModel previous = cnh.getFile();

    MediaFileModel stored = media.store(MediaSlot.DRIVER_CNH_FILE, cnh.getToken(), file);
    cnh.setFile(stored);
    DriverCnhService.applyData(cnh, DriverCnhService.NO_DATA);
    cnhs.save(cnh);

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

  private DriverCnhModel require(String token) {
    return cnhs.findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_cnh.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
