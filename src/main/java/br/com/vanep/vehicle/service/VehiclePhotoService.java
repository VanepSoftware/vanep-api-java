package br.com.vanep.vehicle.service;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehiclePhotoService {

  private final VehicleRepository vehicles;
  private final MediaFileService media;

  public VehiclePhotoService(VehicleRepository vehicles, MediaFileService media) {
    this.vehicles = vehicles;
    this.media = media;
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
      throw ApiProblemException.notFound("media.not_found");
    }
    return photo;
  }

  private static MediaFileModel read(VehicleModel vehicle, MediaSlot slot) {
    return switch (slot) {
      case VEHICLE_PHOTO_FRONT -> vehicle.getPhotoFront();
      case VEHICLE_PHOTO_SIDE -> vehicle.getPhotoSide();
      default -> throw new IllegalArgumentException("Slot que não pertence ao veículo: " + slot);
    };
  }

  private static void write(VehicleModel vehicle, MediaSlot slot, MediaFileModel media) {
    switch (slot) {
      case VEHICLE_PHOTO_FRONT -> vehicle.setPhotoFront(media);
      case VEHICLE_PHOTO_SIDE -> vehicle.setPhotoSide(media);
      default -> throw new IllegalArgumentException("Slot que não pertence ao veículo: " + slot);
    }
  }

  private VehicleModel require(String token) {
    return vehicles
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("vehicle.not_found"));
  }
}
