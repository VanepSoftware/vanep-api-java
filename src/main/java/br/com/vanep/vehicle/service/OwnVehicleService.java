package br.com.vanep.vehicle.service;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnVehicleService {

  private final VehicleRepository vehicles;
  private final MessageSource messages;

  public OwnVehicleService(VehicleRepository vehicles, MessageSource messages) {
    this.vehicles = vehicles;
    this.messages = messages;
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

  private ResponseStatusException vehicleError(boolean forWrite) {
    if (forWrite) {
      return new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          messages.getMessage(
              "unlinked_passenger.vehicle.not_owned", null, LocaleContextHolder.getLocale()));
    }
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        messages.getMessage(
            "driver_student.vehicle.not_found", null, LocaleContextHolder.getLocale()));
  }
}
