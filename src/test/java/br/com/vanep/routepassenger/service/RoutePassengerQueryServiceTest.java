package br.com.vanep.routepassenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.routepassenger.dto.RoutePassengerDTO;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutePassengerQueryServiceTest {

  private static final LocalDate MONDAY = LocalDate.of(2027, 3, 1);

  @Mock private ContractItemRepository items;
  @Mock private UnlinkedPassengerRepository unlinkedPassengers;

  private RoutePassengerQueryService service;
  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    service = new RoutePassengerQueryService(items, unlinkedPassengers);
    carlos = new DriverModel();
    carlos.setId(2L);
  }

  @Test
  void returnsContractedAndUnlinkedStudentsTogetherOrderedByTheirWindow() {
    when(items.findRoutePassengers(2L, MONDAY, DayOfWeek.MONDAY, OperationShift.MORNING))
        .thenReturn(List.of(passenger(PassengerSource.CONTRACT_ITEM, "Ana", LocalTime.of(7, 0))));
    when(unlinkedPassengers.findRoutePassengers(2L, DayOfWeek.MONDAY, OperationShift.MORNING))
        .thenReturn(
            List.of(
                passenger(PassengerSource.UNLINKED_PASSENGER, "Bruno", LocalTime.of(7, 0)),
                passenger(PassengerSource.UNLINKED_PASSENGER, "Lucas", LocalTime.of(6, 40))));

    List<RoutePassengerDTO> passengers =
        service.findPassengers(carlos, MONDAY, OperationShift.MORNING);

    assertThat(passengers)
        .extracting(passenger -> passenger.name(), passenger -> passenger.source())
        .containsExactly(
            tuple("Lucas", PassengerSource.UNLINKED_PASSENGER),
            tuple("Ana", PassengerSource.CONTRACT_ITEM),
            tuple("Bruno", PassengerSource.UNLINKED_PASSENGER));
  }

  @Test
  void returnsNobodyWhenNeitherSourceHasAPassenger() {
    when(items.findRoutePassengers(
            2L, MONDAY.plusDays(1), DayOfWeek.TUESDAY, OperationShift.AFTERNOON))
        .thenReturn(List.of());
    when(unlinkedPassengers.findRoutePassengers(2L, DayOfWeek.TUESDAY, OperationShift.AFTERNOON))
        .thenReturn(List.of());

    assertThat(service.findPassengers(carlos, MONDAY.plusDays(1), OperationShift.AFTERNOON))
        .isEmpty();
  }

  RoutePassengerDTO passenger(PassengerSource source, String name, LocalTime windowStart) {
    return new RoutePassengerDTO(
        source,
        name.toLowerCase(),
        name,
        "school",
        "Escola Municipal",
        RouteLeg.OUTBOUND,
        windowStart,
        null,
        "13015904",
        "Rua A",
        "10",
        null,
        "Centro",
        null,
        "campinas",
        "Campinas",
        null);
  }
}
