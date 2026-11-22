package br.com.vanep.drivercompliance.dto;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import java.util.List;

/** The live, unsuperseded submissions of a driver and of each of their active vans. */
public record DriverComplianceInputDTO(
    DriverModel driver,
    List<DriverCnhModel> cnhs,
    List<DriverDocumentModel> driverDocuments,
    List<VehicleInputDTO> vehicles) {

  public record VehicleInputDTO(VehicleModel vehicle, List<VehicleDocumentModel> documents) {}
}
