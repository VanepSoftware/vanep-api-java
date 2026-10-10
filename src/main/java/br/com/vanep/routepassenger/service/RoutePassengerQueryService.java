package br.com.vanep.routepassenger.service;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutePassengerQueryService {

  static final Comparator<RoutePassengerDTO> BY_WINDOW_THEN_NAME =
      Comparator.comparing((RoutePassengerDTO passenger) -> passenger.windowStart())
          .thenComparing(passenger -> passenger.name());

  private final ContractItemRepository items;
  private final UnlinkedPassengerRepository unlinkedPassengers;

  public RoutePassengerQueryService(
      ContractItemRepository items, UnlinkedPassengerRepository unlinkedPassengers) {
    this.items = items;
    this.unlinkedPassengers = unlinkedPassengers;
  }

  @Transactional(readOnly = true)
  public List<RoutePassengerDTO> findPassengers(
      DriverModel driver, LocalDate serviceDate, OperationShift shift) {
    DayOfWeek weekday = serviceDate.getDayOfWeek();
    List<RoutePassengerDTO> contracted =
        items.findRoutePassengers(driver.getId(), serviceDate, weekday, shift);
    List<RoutePassengerDTO> unlinked =
        unlinkedPassengers.findRoutePassengers(driver.getId(), weekday, shift);
    return Stream.concat(contracted.stream(), unlinked.stream())
        .sorted(BY_WINDOW_THEN_NAME)
        .toList();
  }
}
