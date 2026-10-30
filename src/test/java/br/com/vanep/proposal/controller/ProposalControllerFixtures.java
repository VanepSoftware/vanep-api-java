package br.com.vanep.proposal.controller;

import static br.com.vanep.servicerequest.controller.ServiceRequestControllerFixtures.WEEKDAYS;

import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.proposal.dto.ProposalCreateRequestDTO;
import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.proposal.enums.ProposalAdjustmentPolicy;
import br.com.vanep.proposal.enums.ProposalCancellationPolicy;
import br.com.vanep.proposal.service.ProposalService;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import br.com.vanep.vehicle.model.VehicleModel;
import br.com.vanep.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.ApplicationContext;

public class ProposalControllerFixtures {

  private static final LocalTime OUTBOUND_WINDOW_START = LocalTime.of(6, 40);
  private static final LocalTime RETURN_WINDOW_START = LocalTime.of(12, 10);

  private final VehicleRepository vehicles;
  private final ProposalService service;

  public ProposalControllerFixtures(ApplicationContext context) {
    vehicles = context.getBean(VehicleRepository.class);
    service = context.getBean(ProposalService.class);
  }

  public VehicleModel createVehicle(DriverModel driver, String plate) {
    VehicleModel vehicle = new VehicleModel();
    vehicle.setDriver(driver);
    vehicle.setPlate(plate);
    vehicle.setBrand("Renault");
    vehicle.setModel("Master");
    vehicle.setManufactureYear(2022);
    vehicle.setColor("Branca");
    vehicle.setCapacity(15);
    return vehicles.save(vehicle);
  }

  public String sendProposal(
      DriverModel driver, String requestToken, VehicleModel vehicle, DependentModel... offered) {
    List<ProposalItemRequestDTO> items =
        Stream.of(offered)
            .map(
                dependent ->
                    new ProposalItemRequestDTO(
                        dependent.getToken(), new BigDecimal("420.00"), roundTrips()))
            .toList();
    return service
        .send(
            driver.getUser().getToken(),
            requestToken,
            new ProposalCreateRequestDTO(
                startsOn(),
                endsOn(),
                new BigDecimal("4200.00"),
                10,
                5,
                new BigDecimal("100.00"),
                ProposalAdjustmentPolicy.NONE,
                ProposalCancellationPolicy.NOTICE_30_DAYS,
                vehicle.getToken(),
                null,
                "Saio às 6h20",
                items))
        .token();
  }

  static Map<String, Object> proposalBody(String vehicleToken, List<Map<String, Object>> items) {
    Map<String, Object> body = new HashMap<>();
    body.put("startsOn", startsOn().toString());
    body.put("endsOn", endsOn().toString());
    body.put("totalAmount", "4200.00");
    body.put("installments", 10);
    body.put("dueDay", 5);
    body.put("enrollmentFee", "100.00");
    body.put("adjustmentPolicy", "NONE");
    body.put("cancellationPolicy", "NOTICE_30_DAYS");
    body.put("vehicleToken", vehicleToken);
    body.put("message", "Saio às 6h20");
    body.put("items", items);
    return body;
  }

  static Map<String, Object> proposalItemBody(String dependentToken) {
    return Map.of(
        "dependentToken",
        dependentToken,
        "monthlyAmount",
        "420.00",
        "slots",
        roundTrips().stream()
            .map(
                slot ->
                    Map.of(
                        "weekday", slot.weekday().name(),
                        "leg", slot.leg().name(),
                        "shift", slot.shift().name(),
                        "windowStart", slot.windowStart().toString()))
            .toList());
  }

  static LocalDate startsOn() {
    return LocalDate.now().plusDays(7);
  }

  static LocalDate endsOn() {
    return startsOn().plusMonths(10).minusDays(1);
  }

  static List<ScheduleSlotRequestDTO> roundTrips() {
    return WEEKDAYS.stream()
        .flatMap(
            weekday ->
                Stream.of(
                    new ScheduleSlotRequestDTO(
                        weekday,
                        RouteLeg.OUTBOUND,
                        OperationShift.MORNING,
                        OUTBOUND_WINDOW_START,
                        null),
                    new ScheduleSlotRequestDTO(
                        weekday,
                        RouteLeg.RETURN,
                        OperationShift.AFTERNOON,
                        RETURN_WINDOW_START,
                        null)))
        .toList();
  }
}
