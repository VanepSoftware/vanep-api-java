package br.com.vanep.vehicle.service;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnVehicleService {

  private final VehicleRepository vehicles;

  public OwnVehicleService(VehicleRepository vehicles) {
    this.vehicles = vehicles;
  }

  @Transactional(readOnly = true)
  public Long findOwnVehicleId(DriverModel driver, String vehicleToken) {
    if (vehicleToken == null) {
      return null;
    }
    return requireOwn(driver, vehicleToken, false).getId();
  }

  @Transactional(readOnly = true)
  public VehicleModel requireOwnActiveVehicle(DriverModel driver, String vehicleToken) {
    return requireOwn(driver, vehicleToken, true);
  }

  private VehicleModel requireOwn(DriverModel driver, String vehicleToken, boolean forWrite) {
    VehicleModel vehicle =
        vehicles
            .findByToken(vehicleToken)
            .filter(found -> found.getDriver().getId().equals(driver.getId()))
            .orElse(null);
    if (vehicle == null) {
      throw vehicleError(forWrite);
    }
    if (!vehicle.isActive()) {
      throw vehicleError(true);
    }
    return vehicle;
  }

  private ApiProblemException vehicleError(boolean forWrite) {
    if (forWrite) {
      return ApiProblemException.unprocessable("unlinked_passenger.vehicle.not_owned");
    }
    return ApiProblemException.notFound("driver_student.vehicle.not_found");
  }
}
