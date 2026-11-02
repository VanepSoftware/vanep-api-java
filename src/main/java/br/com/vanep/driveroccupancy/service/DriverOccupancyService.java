package br.com.vanep.driveroccupancy.service;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.service.DriverService;
import br.com.vanep.driveroccupancy.dto.DriverOccupancyResponseDTO;
import br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverOccupancyService {
  private final DriverService drivers;
  private final ContractItemRepository contractItems;
  private final UnlinkedPassengerRepository unlinkedPassengers;

  public DriverOccupancyService(
      DriverService drivers,
      ContractItemRepository contractItems,
      UnlinkedPassengerRepository unlinkedPassengers) {
    this.drivers = drivers;
    this.contractItems = contractItems;
    this.unlinkedPassengers = unlinkedPassengers;
  }

  @Transactional(readOnly = true)
  public DriverOccupancyResponseDTO findMyOccupancy(String callerUid) {
    return findOccupancy(
        drivers.requireCallerDriver(callerUid), LocalDate.now(TripService.SERVICE_ZONE));
  }

  @Transactional(readOnly = true)
  public DriverOccupancyResponseDTO findOccupancy(DriverModel driver, LocalDate today) {
    Long driverId = driver.getId();
    long studentsWithFamily = contractItems.countActiveDependents(driverId, today);
    long students = studentsWithFamily + unlinkedPassengers.countByDriverId(driverId);
    return new DriverOccupancyResponseDTO(
        sumPassengersPerShift(
            contractItems.countActivePassengersByShift(driverId, today),
            unlinkedPassengers.countPassengersByShift(driverId)),
        students,
        studentsWithFamily);
  }

  List<ShiftOccupancyDTO> sumPassengersPerShift(
      List<ShiftOccupancyDTO> contracted, List<ShiftOccupancyDTO> unlinked) {
    Map<OperationShift, Long> passengers = new EnumMap<>(OperationShift.class);
    Stream.concat(contracted.stream(), unlinked.stream())
        .forEach(count -> passengers.merge(count.shift(), count.passengers(), Long::sum));
    return Arrays.stream(OperationShift.values())
        .map(shift -> new ShiftOccupancyDTO(shift, passengers.getOrDefault(shift, 0L)))
        .toList();
  }
}
