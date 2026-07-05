package br.com.vanep.vehicle.mapper;

import br.com.vanep.vehicle.Vehicle;
import br.com.vanep.vehicle.dto.VehicleResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

  public VehicleResponseDTO toResponse(Vehicle vehicle) {
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
