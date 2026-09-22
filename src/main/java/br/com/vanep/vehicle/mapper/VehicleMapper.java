package br.com.vanep.vehicle.mapper;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import br.com.vanep.vehicle.model.VehicleModel;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

  public VehicleResponseDTO toResponse(VehicleModel vehicle) {
    return new VehicleResponseDTO(
        vehicle.getToken(),
        vehicle.getDriver().getToken(),
        vehicle.getPlate(),
        vehicle.getBrand(),
        vehicle.getModel(),
        vehicle.getManufactureYear(),
        vehicle.getColor(),
        vehicle.getCapacity(),
        url(vehicle, "photo-front", vehicle.getPhotoFront()),
        url(vehicle, "photo-side", vehicle.getPhotoSide()),
        url(vehicle, "photo-document", vehicle.getPhotoDocument()),
        vehicle.isActive(),
        vehicle.getCreatedAt());
  }

  private static String url(VehicleModel vehicle, String slot, MediaFileModel media) {
    return MediaUrl.of("/api/vehicles", vehicle.getToken(), slot, media);
  }
}
