package br.com.vanep.vehicle.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehiclePhotoService {

  private final VehicleRepository vehicles;
  private final MediaFileService media;
  private final MessageSource messages;

  public VehiclePhotoService(
      VehicleRepository vehicles, MediaFileService media, MessageSource messages) {
    this.vehicles = vehicles;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public MediaFileModel replace(String token, MediaSlot slot, MultipartFile file) {
    VehicleModel vehicle = require(token);
    MediaFileModel previous = read(vehicle, slot);

    MediaFileModel stored = media.store(slot, vehicle.getToken(), file);
    write(vehicle, slot, stored);
    vehicles.save(vehicle);

    media.discard(previous);
    return stored;
  }

  @Transactional(readOnly = true)
  public MediaFileModel require(String token, MediaSlot slot) {
    MediaFileModel photo = read(require(token), slot);
    if (photo == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("media.not_found"));
    }
    return photo;
  }

  private static MediaFileModel read(VehicleModel vehicle, MediaSlot slot) {
    return switch (slot) {
      case VEHICLE_PHOTO_FRONT -> vehicle.getPhotoFront();
      case VEHICLE_PHOTO_SIDE -> vehicle.getPhotoSide();
      case VEHICLE_PHOTO_DOCUMENT -> vehicle.getPhotoDocument();
      default -> throw new IllegalArgumentException("Slot que não pertence ao veículo: " + slot);
    };
  }

  private static void write(VehicleModel vehicle, MediaSlot slot, MediaFileModel media) {
    switch (slot) {
      case VEHICLE_PHOTO_FRONT -> vehicle.setPhotoFront(media);
      case VEHICLE_PHOTO_SIDE -> vehicle.setPhotoSide(media);
      case VEHICLE_PHOTO_DOCUMENT -> vehicle.setPhotoDocument(media);
      default -> throw new IllegalArgumentException("Slot que não pertence ao veículo: " + slot);
    }
  }

  private VehicleModel require(String token) {
    return vehicles
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("vehicle.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
