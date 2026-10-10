package br.com.vanep.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.enums.ScheduleSlotViolation;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleSlotPolicyTest {

  private final ScheduleSlotPolicy policy = new ScheduleSlotPolicy();

  @Test
  void anEmptyScheduleIsRefused() {
    assertThat(policy.validate(List.of()))
        .contains(ScheduleSlotViolation.SLOTS_REQUIRED)
        .map(violation -> violation.messageKey())
        .contains("schedule.slots.required");
  }

  @Test
  void aMissingSlotListIsRefused() {
    assertThat(policy.validate(null)).contains(ScheduleSlotViolation.SLOTS_REQUIRED);
  }

  @Test
  void theSameWeekdayAndLegTwiceIsRefused() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(
            outbound(DayOfWeek.MONDAY, LocalTime.of(6, 40), LocalTime.of(6, 50)),
            outbound(DayOfWeek.MONDAY, LocalTime.of(7, 10), null));

    assertThat(policy.validate(slots))
        .contains(ScheduleSlotViolation.SLOT_DUPLICATE)
        .map(violation -> violation.messageKey())
        .contains("schedule.slot.duplicate");
  }

  @Test
  void theSameLegOnDifferentWeekdaysIsAccepted() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(
            outbound(DayOfWeek.MONDAY, LocalTime.of(6, 40), null),
            outbound(DayOfWeek.TUESDAY, LocalTime.of(6, 40), null));

    assertThat(policy.validate(slots)).isEmpty();
  }

  @Test
  void aWindowEndBeforeItsStartIsRefused() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(outbound(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(6, 50)));

    assertThat(policy.validate(slots))
        .contains(ScheduleSlotViolation.WINDOW_INVALID)
        .map(violation -> violation.messageKey())
        .contains("schedule.slot.window_invalid");
  }

  @Test
  void aWindowEndingWhenItStartsIsRefused() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(outbound(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(7, 0)));

    assertThat(policy.validate(slots)).contains(ScheduleSlotViolation.WINDOW_INVALID);
  }

  @Test
  void aWindowWithoutEndIsAccepted() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(outbound(DayOfWeek.MONDAY, LocalTime.of(6, 40), null));

    assertThat(policy.validate(slots)).isEmpty();
  }

  @Test
  void aFullTimeDayWithMorningOutboundAndAfternoonReturnIsAccepted() {
    List<ScheduleSlotRequestDTO> slots =
        List.of(
            outbound(DayOfWeek.MONDAY, LocalTime.of(6, 40), LocalTime.of(6, 50)),
            new ScheduleSlotRequestDTO(
                DayOfWeek.MONDAY,
                RouteLeg.RETURN,
                OperationShift.AFTERNOON,
                LocalTime.of(17, 0),
                null));

    assertThat(policy.validate(slots)).isEmpty();
  }

  private ScheduleSlotRequestDTO outbound(
      DayOfWeek weekday, LocalTime windowStart, LocalTime windowEnd) {
    return new ScheduleSlotRequestDTO(
        weekday, RouteLeg.OUTBOUND, OperationShift.MORNING, windowStart, windowEnd);
  }
}
