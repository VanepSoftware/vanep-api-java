package br.com.vanep.schedule.mapper;

import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMapper {

  static final Comparator<ScheduleSlotModel> BY_WEEKDAY_THEN_LEG =
      Comparator.comparing((ScheduleSlotModel slot) -> slot.getWeekday())
          .thenComparing(slot -> slot.getLeg());

  public List<ScheduleSlotResponseDTO> toSlotResponses(ScheduleModel schedule) {
    return schedule.getSlots().stream()
        .sorted(BY_WEEKDAY_THEN_LEG)
        .map(slot -> toSlotResponse(slot))
        .toList();
  }

  public ScheduleSlotResponseDTO toSlotResponse(ScheduleSlotModel slot) {
    return new ScheduleSlotResponseDTO(
        slot.getWeekday(),
        slot.getLeg(),
        slot.getShift(),
        slot.getWindowStart(),
        slot.getWindowEnd());
  }
}
