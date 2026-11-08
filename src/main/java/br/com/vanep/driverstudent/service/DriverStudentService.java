package br.com.vanep.driverstudent.service;

import br.com.vanep.contract.repository.ContractItemRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverstudent.dto.ContractStudentRowDTO;
import br.com.vanep.driverstudent.dto.DriverStudentResponseDTO;
import br.com.vanep.driverstudent.dto.UnlinkedStudentRowDTO;
import br.com.vanep.driverstudent.enums.FamilyStatus;
import br.com.vanep.routepassenger.enums.PassengerSource;
import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.schedule.mapper.ScheduleMapper;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.trip.service.TripService;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerFamilyKeyDTO;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerFamilyService;
import br.com.vanep.unlinkedpassenger.service.UnlinkedPassengerService;
import br.com.vanep.vehicle.service.OwnVehicleService;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverStudentService {

  static final Comparator<DriverStudentResponseDTO> BY_NAME =
      Comparator.comparing(
          (DriverStudentResponseDTO student) -> student.name(),
          Collator.getInstance(Locale.of("pt", "BR")));

  private final UnlinkedPassengerService passengerService;
  private final OwnVehicleService ownVehicles;
  private final ContractItemRepository contractItems;
  private final UnlinkedPassengerRepository passengers;
  private final UnlinkedPassengerFamilyService families;
  private final ScheduleRepository schedules;
  private final ScheduleMapper scheduleMapper;
  private final FamilyStatusPolicy statusPolicy;

  public DriverStudentService(
      UnlinkedPassengerService passengerService,
      OwnVehicleService ownVehicles,
      ContractItemRepository contractItems,
      UnlinkedPassengerRepository passengers,
      UnlinkedPassengerFamilyService families,
      ScheduleRepository schedules,
      ScheduleMapper scheduleMapper,
      FamilyStatusPolicy statusPolicy) {
    this.passengerService = passengerService;
    this.ownVehicles = ownVehicles;
    this.contractItems = contractItems;
    this.passengers = passengers;
    this.families = families;
    this.schedules = schedules;
    this.scheduleMapper = scheduleMapper;
    this.statusPolicy = statusPolicy;
  }

  @Transactional(readOnly = true)
  public List<DriverStudentResponseDTO> findMyStudents(String callerUid, String vehicleToken) {
    DriverModel driver = passengerService.requireDriver(callerUid);
    Long vehicleId = ownVehicles.findOwnVehicleId(driver, vehicleToken);
    LocalDate today = LocalDate.now(TripService.SERVICE_ZONE);
    List<ContractStudentRowDTO> contracted =
        contractItems.findActiveStudentsOfDriver(driver.getId(), today);
    List<UnlinkedStudentRowDTO> unlinked =
        passengers.findStudentsOfDriver(driver.getId(), vehicleId);
    Map<Long, List<ScheduleSlotResponseDTO>> slotsBySchedule = findSlots(contracted, unlinked);
    Map<Long, UnlinkedPassengerFamilyDTO> familyByPassenger = findFamilies(unlinked);
    return Stream.concat(
            contracted.stream().map(row -> toResponse(row, slotsBySchedule)),
            unlinked.stream()
                .map(
                    row ->
                        toResponse(
                            row,
                            slotsBySchedule,
                            familyByPassenger.getOrDefault(
                                row.id(), UnlinkedPassengerFamilyDTO.NONE))))
        .sorted(BY_NAME)
        .toList();
  }

  Map<Long, List<ScheduleSlotResponseDTO>> findSlots(
      List<ContractStudentRowDTO> contracted, List<UnlinkedStudentRowDTO> unlinked) {
    List<Long> scheduleIds =
        Stream.concat(
                contracted.stream().map(row -> row.scheduleId()),
                unlinked.stream().map(row -> row.scheduleId()))
            .toList();
    if (scheduleIds.isEmpty()) {
      return Map.of();
    }
    return scheduleMapper.groupSlotRowsBySchedule(
        schedules.findSlotRowsByScheduleIdIn(scheduleIds));
  }

  Map<Long, UnlinkedPassengerFamilyDTO> findFamilies(List<UnlinkedStudentRowDTO> unlinked) {
    return families.findFamiliesByKeys(
        unlinked.stream()
            .map(
                row ->
                    new UnlinkedPassengerFamilyKeyDTO(row.id(), row.vehicleId(), row.dependentId()))
            .toList());
  }

  DriverStudentResponseDTO toResponse(
      ContractStudentRowDTO row, Map<Long, List<ScheduleSlotResponseDTO>> slotsBySchedule) {
    return new DriverStudentResponseDTO(
        row.itemToken(),
        PassengerSource.CONTRACT_ITEM,
        row.dependentName(),
        row.schoolToken(),
        row.schoolName(),
        null,
        slotsBySchedule.getOrDefault(row.scheduleId(), List.of()),
        null,
        row.dependentToken(),
        FamilyStatus.WITH_FAMILY,
        null,
        null);
  }

  DriverStudentResponseDTO toResponse(
      UnlinkedStudentRowDTO row,
      Map<Long, List<ScheduleSlotResponseDTO>> slotsBySchedule,
      UnlinkedPassengerFamilyDTO family) {
    return new DriverStudentResponseDTO(
        row.token(),
        PassengerSource.UNLINKED_PASSENGER,
        row.name(),
        row.schoolToken(),
        row.schoolName(),
        row.schoolShift(),
        slotsBySchedule.getOrDefault(row.scheduleId(), List.of()),
        family.vehicleToken(),
        family.dependentToken(),
        statusPolicy.deriveUnlinkedStatus(family),
        family.invitedAt(),
        family.familyMatch());
  }
}
