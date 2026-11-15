package br.com.vanep.schedule.mapper;

import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.schedule.dto.ScheduleSlotRowDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMapper {

  static final Comparator<ScheduleSlotResponseDTO> BY_WEEKDAY_THEN_LEG =
      Comparator.comparing((ScheduleSlotResponseDTO slot) -> slot.weekday())
          .thenComparing(slot -> slot.leg());

  public List<ScheduleSlotResponseDTO> toSlotResponses(ScheduleModel schedule) {
    return schedule.getSlots().stream()
        .map(slot -> toSlotResponse(slot))
        .sorted(BY_WEEKDAY_THEN_LEG)
        .toList();
  }

  public Map<Long, List<ScheduleSlotResponseDTO>> groupSlotRowsBySchedule(
      List<ScheduleSlotRowDTO> rows) {
    return rows.stream().collect(Collectors.groupingBy(row -> row.scheduleId())).entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> entry.getKey(),
                entry ->
                    entry.getValue().stream()
                        .map(row -> toSlotResponse(row))
                        .sorted(BY_WEEKDAY_THEN_LEG)
                        .toList()));
  }

  public ScheduleSlotResponseDTO toSlotResponse(ScheduleSlotModel slot) {
    return new ScheduleSlotResponseDTO(
        slot.getWeekday(), slot.getLeg(), slot.getShift(), slot.getPickupTime());
  }

  ScheduleSlotResponseDTO toSlotResponse(ScheduleSlotRowDTO row) {
    return new ScheduleSlotResponseDTO(row.weekday(), row.leg(), row.shift(), row.pickupTime());
  }
}
