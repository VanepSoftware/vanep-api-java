package br.com.vanep.drivercompliance.service;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.drivercnh.model.DriverCnhModel;
import br.com.vanep.drivercnh.repository.DriverCnhRepository;
import br.com.vanep.drivercompliance.dto.DriverComplianceDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO;
import br.com.vanep.drivercompliance.dto.DriverComplianceInputDTO.VehicleInputDTO;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.driverdocument.repository.DriverDocumentRepository;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import br.com.vanep.vehicledocument.repository.VehicleDocumentRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverComplianceService {

  private final DriverCnhRepository cnhs;
  private final DriverDocumentRepository driverDocuments;
  private final VehicleRepository vehicles;
  private final VehicleDocumentRepository vehicleDocuments;
  private final DriverCompliancePolicy policy;

  public DriverComplianceService(
      DriverCnhRepository cnhs,
      DriverDocumentRepository driverDocuments,
      VehicleRepository vehicles,
      VehicleDocumentRepository vehicleDocuments,
      DriverCompliancePolicy policy) {
    this.cnhs = cnhs;
    this.driverDocuments = driverDocuments;
    this.vehicles = vehicles;
    this.vehicleDocuments = vehicleDocuments;
    this.policy = policy;
  }

  @Transactional(readOnly = true)
  public Map<Long, DriverComplianceDTO> evaluateAll(
      Collection<DriverModel> drivers, LocalDate date) {
    Map<Long, DriverComplianceDTO> evaluations = new LinkedHashMap<>();
    loadInputs(drivers)
        .forEach(input -> evaluations.put(input.driver().getId(), policy.evaluate(input, date)));
    return evaluations;
  }

  @Transactional(readOnly = true)
  public DriverComplianceDTO evaluate(DriverModel driver, LocalDate date) {
    return policy.evaluate(loadInput(driver), date);
  }

  @Transactional(readOnly = true)
  public DriverComplianceInputDTO loadInput(DriverModel driver) {
    return loadInputs(List.of(driver)).get(0);
  }

  // Four queries for any number of drivers, vans and documents (rule 17).
  @Transactional(readOnly = true)
  public List<DriverComplianceInputDTO> loadInputs(Collection<DriverModel> drivers) {
    if (drivers.isEmpty()) {
      return List.of();
    }
    List<Long> driverIds = drivers.stream().map(driver -> driver.getId()).toList();
    Map<Long, List<DriverCnhModel>> cnhsByDriver =
        cnhs.findByDriverIdInAndSupersededAtIsNull(driverIds).stream()
            .collect(Collectors.groupingBy(cnh -> cnh.getDriver().getId()));
    Map<Long, List<DriverDocumentModel>> documentsByDriver =
        driverDocuments.findByDriverIdInAndSupersededAtIsNull(driverIds).stream()
            .collect(Collectors.groupingBy(document -> document.getDriver().getId()));
    List<VehicleModel> activeVehicles =
        vehicles.findByDriverIdInAndActiveTrueOrderByCreatedAtAscIdAsc(driverIds);
    Map<Long, List<VehicleDocumentModel>> documentsByVehicle =
        activeVehicles.isEmpty()
            ? Map.of()
            : vehicleDocuments
                .findUnsupersededByVehicleIdIn(
                    activeVehicles.stream().map(vehicle -> vehicle.getId()).toList())
                .stream()
                .collect(Collectors.groupingBy(document -> document.getVehicle().getId()));
    Map<Long, List<VehicleInputDTO>> vehiclesByDriver =
        activeVehicles.stream()
            .collect(
                Collectors.groupingBy(
                    vehicle -> vehicle.getDriver().getId(),
                    Collectors.mapping(
                        vehicle ->
                            new VehicleInputDTO(
                                vehicle,
                                documentsByVehicle.getOrDefault(vehicle.getId(), List.of())),
                        Collectors.toList())));
    return drivers.stream()
        .map(
            driver ->
                new DriverComplianceInputDTO(
                    driver,
                    cnhsByDriver.getOrDefault(driver.getId(), List.of()),
                    documentsByDriver.getOrDefault(driver.getId(), List.of()),
                    vehiclesByDriver.getOrDefault(driver.getId(), List.of())))
        .toList();
  }
}
