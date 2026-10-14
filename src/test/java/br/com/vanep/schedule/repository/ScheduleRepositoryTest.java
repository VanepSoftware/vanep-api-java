package br.com.vanep.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ScheduleRepositoryTest {

  @Autowired private ScheduleRepository schedules;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void savesAScheduleWithItsSlotsAndReadsThemBack() {
    ScheduleModel saved = schedules.save(fullTimeMonday());

    ScheduleModel reread = schedules.findWithSlotsById(saved.getId()).orElseThrow();

    assertThat(reread.getSlots())
        .extracting(
            slot -> slot.getWeekday(),
            slot -> slot.getLeg(),
            slot -> slot.getShift(),
            slot -> slot.getWindowStart(),
            slot -> slot.getWindowEnd())
        .containsExactlyInAnyOrder(
            tuple(
                DayOfWeek.MONDAY,
                RouteLeg.OUTBOUND,
                OperationShift.MORNING,
                LocalTime.of(6, 40),
                LocalTime.of(6, 50)),
            tuple(
                DayOfWeek.MONDAY,
                RouteLeg.RETURN,
                OperationShift.AFTERNOON,
                LocalTime.of(17, 0),
                null));
  }

  @Test
  void aSlotRemovedFromTheScheduleIsSoftDeletedNotErased() {
    ScheduleModel saved = schedules.save(fullTimeMonday());
    ScheduleModel loaded = schedules.findWithSlotsById(saved.getId()).orElseThrow();

    loaded.getSlots().removeIf(slot -> slot.getLeg() == RouteLeg.RETURN);
    schedules.save(loaded);

    assertThat(schedules.findWithSlotsById(saved.getId()).orElseThrow().getSlots())
        .extracting(slot -> slot.getLeg())
        .containsExactly(RouteLeg.OUTBOUND);
    assertThat(countSoftDeletedSlots()).isEqualTo(1);
    assertThat(countAllSlots()).isEqualTo(2);
  }

  @Test
  void replacingTheSlotsSoftDeletesTheOldOnesAndKeepsOnlyTheNewOnes() {
    ScheduleModel saved = schedules.save(fullTimeMonday());
    ScheduleModel loaded = schedules.findWithSlotsById(saved.getId()).orElseThrow();

    loaded.getSlots().clear();
    loaded.addSlot(
        slot(
            DayOfWeek.TUESDAY,
            RouteLeg.OUTBOUND,
            OperationShift.MORNING,
            LocalTime.of(7, 0),
            null));
    schedules.save(loaded);

    assertThat(schedules.findWithSlotsById(saved.getId()).orElseThrow().getSlots())
        .extracting(slot -> slot.getWeekday())
        .containsExactly(DayOfWeek.TUESDAY);
    assertThat(countSoftDeletedSlots()).isEqualTo(2);
    assertThat(countAllSlots()).isEqualTo(3);
  }

  private ScheduleModel fullTimeMonday() {
    ScheduleModel schedule = new ScheduleModel();
    schedule.addSlot(
        slot(
            DayOfWeek.MONDAY,
            RouteLeg.OUTBOUND,
            OperationShift.MORNING,
            LocalTime.of(6, 40),
            LocalTime.of(6, 50)));
    schedule.addSlot(
        slot(
            DayOfWeek.MONDAY,
            RouteLeg.RETURN,
            OperationShift.AFTERNOON,
            LocalTime.of(17, 0),
            null));
    return schedule;
  }

  private ScheduleSlotModel slot(
      DayOfWeek weekday,
      RouteLeg leg,
      OperationShift shift,
      LocalTime windowStart,
      LocalTime windowEnd) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(weekday);
    slot.setLeg(leg);
    slot.setShift(shift);
    slot.setWindowStart(windowStart);
    slot.setWindowEnd(windowEnd);
    return slot;
  }

  private Integer countAllSlots() {
    return jdbc.queryForObject("select count(*) from schedule_slot", Integer.class);
  }

  private Integer countSoftDeletedSlots() {
    return jdbc.queryForObject(
        "select count(*) from schedule_slot where deleted_at is not null", Integer.class);
  }
}
