package br.com.vanep.driver.mapper;

import br.com.vanep.driver.dto.DriverMeSummaryResponseDTO;
import br.com.vanep.driver.dto.DriverProfileResponseDTO;
import br.com.vanep.driver.dto.DriverProfileVehicleResponseDTO;
import br.com.vanep.driver.dto.DriverResponseDTO;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.media.web.MediaUrl;
import br.com.vanep.user.dto.UserMeResponseDTO;
import br.com.vanep.vehicle.model.VehicleModel;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DriverMapper {
  public DriverResponseDTO toResponse(DriverModel driver) {
    return new DriverResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getUser().getEmail(),
        driver.getUser().getPhone(),
        driver.getUser().getDocument(),
        photoUrl(driver),
        driver.getRating(),
        driver.getBio(),
        driver.getCnpj(),
        driver.getExperienceYears(),
        driver.getBasePrice(),
        driver.getWorkStartTime(),
        driver.getWorkEndTime(),
        driver.getWorkDays(),
        driver.getWaitToleranceMinutes(),
        driver.getApprovalStatus(),
        driver.isActive(),
        driver.isAvailable(),
        driver.getCreatedAt(),
        driver.getUpdatedAt());
  }

  public DriverMeSummaryResponseDTO toMeSummary(DriverModel driver, UserMeResponseDTO user) {
    return new DriverMeSummaryResponseDTO(
        driver.getToken(),
        photoUrl(driver),
        driver.getRating(),
        driver.getApprovalStatus(),
        driver.isAvailable(),
        driver.isActive(),
        user);
  }

  public DriverProfileResponseDTO toProfile(
      DriverModel driver, List<String> serviceAreas, List<VehicleModel> vehicles) {
    return new DriverProfileResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getUser().getPhone(),
        photoUrl(driver),
        driver.getRating(),
        driver.getBio(),
        driver.getExperienceYears(),
        driver.getBasePrice(),
        driver.isAvailable(),
        serviceAreas,
        vehicles.stream().map(this::toProfileVehicle).toList());
  }

  DriverProfileVehicleResponseDTO toProfileVehicle(VehicleModel vehicle) {
    return new DriverProfileVehicleResponseDTO(
        vehicle.getToken(),
        vehicle.getBrand(),
        vehicle.getModel(),
        vehicle.getManufactureYear(),
        vehicle.getColor(),
        vehicle.getCapacity(),
        MediaUrl.of("/api/vehicles", vehicle.getToken(), "photo-front", vehicle.getPhotoFront()),
        MediaUrl.of("/api/vehicles", vehicle.getToken(), "photo-side", vehicle.getPhotoSide()));
  }

  private String photoUrl(DriverModel driver) {
    return MediaUrl.of("/api/drivers", driver.getToken(), "photo", driver.getPhoto());
  }
}
