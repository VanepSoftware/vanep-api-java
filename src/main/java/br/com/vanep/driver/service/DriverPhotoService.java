package br.com.vanep.driver.service;

import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.error.ApiProblemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DriverPhotoService {

  private final DriverRepository drivers;
  private final MediaFileService media;

  public DriverPhotoService(DriverRepository drivers, MediaFileService media) {
    this.drivers = drivers;
    this.media = media;
  }

  @Transactional
  public MediaFileModel replace(String token, MultipartFile file) {
    DriverModel driver = require(token);
    MediaFileModel previous = driver.getPhoto();

    MediaFileModel stored = media.store(MediaSlot.DRIVER_PHOTO, driver.getToken(), file);
    driver.setPhoto(stored);
    drivers.save(driver);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel require(String token, boolean mustHavePhoto) {
    MediaFileModel photo = require(token).getPhoto();
    if (mustHavePhoto && photo == null) {
      throw ApiProblemException.notFound("media.not_found");
    }
    return photo;
  }

  private DriverModel require(String token) {
    return drivers
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("driver.not_found"));
  }
}
