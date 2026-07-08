package br.com.vanep.vehicle.mapper;

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
        vehicle.getPhotoFrontUrl(),
        vehicle.getPhotoSideUrl(),
        vehicle.getPhotoDocumentUrl(),
        vehicle.isActive(),
        vehicle.getCreatedAt());
  }
}
