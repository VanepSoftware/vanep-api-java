package br.com.vanep.driveroccupancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driver.service.DriverService;
import br.com.vanep.driveroccupancy.dto.DriverOccupancyResponseDTO;
import br.com.vanep.driveroccupancy.dto.ShiftOccupancyDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverOccupancyServiceTest {
  private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

  @Mock private DriverService drivers;
  @Mock private ContractItemRepository contractItems;
  @Mock private UnlinkedPassengerRepository unlinkedPassengers;

  private DriverOccupancyService service;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    service = new DriverOccupancyService(drivers, contractItems, unlinkedPassengers);
    driver = new DriverModel();
    driver.setId(7L);
  }

  @Test
  void addsContractedAndUnlinkedPassengersPerShiftInEnumOrder() {
    when(contractItems.countActivePassengersByShift(7L, TODAY))
        .thenReturn(
            List.of(
                new ShiftOccupancyDTO(OperationShift.AFTERNOON, 9L),
                new ShiftOccupancyDTO(OperationShift.MORNING, 14L)));
    when(unlinkedPassengers.countPassengersByShift(7L))
        .thenReturn(
            List.of(
                new ShiftOccupancyDTO(OperationShift.MORNING, 2L),
                new ShiftOccupancyDTO(OperationShift.AFTERNOON, 3L)));
    when(contractItems.countActiveDependents(7L, TODAY)).thenReturn(24L);
    when(unlinkedPassengers.countByDriverId(7L)).thenReturn(4L);

    DriverOccupancyResponseDTO occupancy = service.findOccupancy(driver, TODAY);

    assertThat(occupancy.shifts())
        .extracting(count -> count.shift(), count -> count.passengers())
        .containsExactly(
            tuple(OperationShift.MORNING, 16L),
            tuple(OperationShift.AFTERNOON, 12L),
            tuple(OperationShift.NIGHT, 0L));
    assertThat(occupancy.students()).isEqualTo(28L);
    assertThat(occupancy.studentsWithFamily()).isEqualTo(24L);
  }

  @Test
  void answersEveryShiftWithZeroWhenTheDriverHasNoPassengers() {
    when(contractItems.countActivePassengersByShift(7L, TODAY)).thenReturn(List.of());
    when(unlinkedPassengers.countPassengersByShift(7L)).thenReturn(List.of());

    DriverOccupancyResponseDTO occupancy = service.findOccupancy(driver, TODAY);

    assertThat(occupancy.shifts())
        .extracting(count -> count.shift(), count -> count.passengers())
        .containsExactly(
            tuple(OperationShift.MORNING, 0L),
            tuple(OperationShift.AFTERNOON, 0L),
            tuple(OperationShift.NIGHT, 0L));
    assertThat(occupancy.students()).isZero();
    assertThat(occupancy.studentsWithFamily()).isZero();
  }

  @Test
  void resolvesTheCallersDriverForTheirOwnOccupancy() {
    when(drivers.requireCallerDriver("driver-uid")).thenReturn(driver);

    DriverOccupancyResponseDTO occupancy = service.findMyOccupancy("driver-uid");

    assertThat(occupancy.shifts()).hasSize(OperationShift.values().length);
  }
}
