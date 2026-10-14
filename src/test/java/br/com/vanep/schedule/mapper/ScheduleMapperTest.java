package br.com.vanep.schedule.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.schedule.dto.ScheduleSlotResponseDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleMapperTest {

  private final ScheduleMapper mapper = new ScheduleMapper();

  @Test
  void ordersTheSlotsByWeekdayThenOutboundBeforeReturn() {
    ScheduleModel schedule = new ScheduleModel();
    schedule.addSlot(slot(DayOfWeek.FRIDAY, RouteLeg.RETURN, OperationShift.AFTERNOON));
    schedule.addSlot(slot(DayOfWeek.MONDAY, RouteLeg.RETURN, OperationShift.AFTERNOON));
    schedule.addSlot(slot(DayOfWeek.WEDNESDAY, RouteLeg.OUTBOUND, OperationShift.MORNING));
    schedule.addSlot(slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, OperationShift.MORNING));

    List<ScheduleSlotResponseDTO> response = mapper.toSlotResponses(schedule);

    assertThat(response)
        .extracting(slot -> slot.weekday(), slot -> slot.leg())
        .containsExactly(
            tuple(DayOfWeek.MONDAY, RouteLeg.OUTBOUND),
            tuple(DayOfWeek.MONDAY, RouteLeg.RETURN),
            tuple(DayOfWeek.WEDNESDAY, RouteLeg.OUTBOUND),
            tuple(DayOfWeek.FRIDAY, RouteLeg.RETURN));
  }

  @Test
  void copiesEveryFieldOfTheSlot() {
    ScheduleSlotModel slot = slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND, OperationShift.MORNING);
    slot.setWindowEnd(LocalTime.of(6, 50));

    assertThat(mapper.toSlotResponse(slot))
        .isEqualTo(
            new ScheduleSlotResponseDTO(
                DayOfWeek.MONDAY,
                RouteLeg.OUTBOUND,
                OperationShift.MORNING,
                LocalTime.of(6, 40),
                LocalTime.of(6, 50)));
  }

  private ScheduleSlotModel slot(DayOfWeek weekday, RouteLeg leg, OperationShift shift) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(weekday);
    slot.setLeg(leg);
    slot.setShift(shift);
    slot.setWindowStart(LocalTime.of(6, 40));
    return slot;
  }
}
