package br.com.vanep.vehicle.service;

import br.com.vanep.driver.model.DriverModel;
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
    return vehicles
        .findByToken(vehicleToken)
        .filter(vehicle -> vehicle.getDriver().getId().equals(driver.getId()))
        .map(vehicle -> vehicle.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messages.getMessage(
                        "driver_student.vehicle.not_found",
                        null,
                        LocaleContextHolder.getLocale())));
  }
}
