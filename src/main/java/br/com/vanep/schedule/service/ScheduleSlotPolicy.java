package br.com.vanep.schedule.service;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.enums.ScheduleSlotViolation;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ScheduleSlotPolicy {

  public Optional<ScheduleSlotViolation> validate(List<ScheduleSlotRequestDTO> slots) {
    if (slots == null || slots.isEmpty()) {
      return Optional.of(ScheduleSlotViolation.SLOTS_REQUIRED);
    }
    if (slots.stream().anyMatch(slot -> hasInvalidWindow(slot))) {
      return Optional.of(ScheduleSlotViolation.WINDOW_INVALID);
    }
    if (hasRepeatedWeekdayAndLeg(slots)) {
      return Optional.of(ScheduleSlotViolation.SLOT_DUPLICATE);
    }
    return Optional.empty();
  }

  boolean hasInvalidWindow(ScheduleSlotRequestDTO slot) {
    return slot.windowEnd() != null && !slot.windowEnd().isAfter(slot.windowStart());
  }

  boolean hasRepeatedWeekdayAndLeg(List<ScheduleSlotRequestDTO> slots) {
    long distinctWeekdayLegs =
        slots.stream().map(slot -> new WeekdayLeg(slot.weekday(), slot.leg())).distinct().count();
    return distinctWeekdayLegs < slots.size();
  }

  record WeekdayLeg(DayOfWeek weekday, RouteLeg leg) {}
}
