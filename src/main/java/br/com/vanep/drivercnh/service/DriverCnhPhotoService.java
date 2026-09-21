package br.com.vanep.drivercnh.service;

import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
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
public class DriverCnhPhotoService {

  private final DriverCnhRepository cnhs;
  private final MediaFileService media;
  private final MessageSource messages;

  public DriverCnhPhotoService(
      DriverCnhRepository cnhs, MediaFileService media, MessageSource messages) {
    this.cnhs = cnhs;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    DriverCnhModel cnh = require(token);
    MediaFileModel previous = cnh.getPhoto();

    MediaFileModel stored = media.store(MediaSlot.DRIVER_CNH_PHOTO, cnh.getToken(), file);
    cnh.setPhoto(stored);
    cnhs.save(cnh);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel requirePhoto(String token) {
    MediaFileModel photo = require(token).getPhoto();
    if (photo == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("media.not_found"));
    }
    return photo;
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
