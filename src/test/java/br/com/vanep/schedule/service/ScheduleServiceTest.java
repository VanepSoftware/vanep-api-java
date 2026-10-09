package br.com.vanep.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceTest {

  private static final ScheduleSlotRequestDTO MONDAY_OUTBOUND =
      new ScheduleSlotRequestDTO(
          DayOfWeek.MONDAY,
          RouteLeg.OUTBOUND,
          OperationShift.MORNING,
          LocalTime.of(6, 40),
          LocalTime.of(6, 50));
  private static final ScheduleSlotRequestDTO MONDAY_RETURN =
      new ScheduleSlotRequestDTO(
          DayOfWeek.MONDAY, RouteLeg.RETURN, OperationShift.AFTERNOON, LocalTime.of(17, 0), null);

  @Mock private ScheduleRepository schedules;
  @Mock private MessageSource messages;

  private ScheduleService service;

  @BeforeEach
  void setUp() {
    when(messages.getMessage(anyString(), any(), any())).thenAnswer(call -> call.getArgument(0));
    when(schedules.save(any())).thenAnswer(call -> call.getArgument(0));
    service = new ScheduleService(schedules, new ScheduleSlotPolicy(), messages);
  }

  @Test
  void createsAScheduleWithTheRequestedSlots() {
    ScheduleModel created = service.create(List.of(MONDAY_OUTBOUND, MONDAY_RETURN));

    verify(schedules).save(created);
    assertThat(created.getSlots())
        .extracting(
            slot -> slot.getSchedule(),
            slot -> slot.getWeekday(),
            slot -> slot.getLeg(),
            slot -> slot.getShift(),
            slot -> slot.getWindowStart(),
            slot -> slot.getWindowEnd())
        .containsExactly(
            tuple(
                created,
                DayOfWeek.MONDAY,
                RouteLeg.OUTBOUND,
                OperationShift.MORNING,
                LocalTime.of(6, 40),
                LocalTime.of(6, 50)),
            tuple(
                created,
                DayOfWeek.MONDAY,
                RouteLeg.RETURN,
                OperationShift.AFTERNOON,
                LocalTime.of(17, 0),
                null));
  }

  @Test
  void aScheduleRefusedByThePolicyIsNotSaved() {
    assertThatThrownBy(() -> service.create(List.of(MONDAY_OUTBOUND, MONDAY_OUTBOUND)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("schedule.slot.duplicate");

    verify(schedules, never()).save(any());
  }

  @Test
  void anEmptyScheduleIsRefused() {
    assertThatThrownBy(() -> service.create(List.of()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("schedule.slots.required");
  }

  @Test
  void replacingFlushesTheRemovalBeforeSavingTheNewSlots() {
    ScheduleModel schedule = scheduleWith(MONDAY_OUTBOUND, MONDAY_RETURN);
    ScheduleSlotRequestDTO tuesdayOutbound =
        new ScheduleSlotRequestDTO(
            DayOfWeek.TUESDAY, RouteLeg.OUTBOUND, OperationShift.MORNING, LocalTime.of(7, 0), null);

    ScheduleModel replaced = service.replace(schedule, List.of(tuesdayOutbound));

    InOrder order = inOrder(schedules);
    order.verify(schedules).flush();
    order.verify(schedules).save(schedule);
    assertThat(replaced.getSlots())
        .extracting(slot -> slot.getWeekday(), slot -> slot.getLeg())
        .containsExactly(tuple(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND));
  }

  @Test
  void aRefusedReplacementKeepsTheCurrentSlots() {
    ScheduleModel schedule = scheduleWith(MONDAY_OUTBOUND, MONDAY_RETURN);
    List<ScheduleSlotModel> current = List.copyOf(schedule.getSlots());

    assertThatThrownBy(() -> service.replace(schedule, List.of()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("schedule.slots.required");

    assertThat(schedule.getSlots()).containsExactlyElementsOf(current);
    verify(schedules, never()).save(any());
  }

  private ScheduleModel scheduleWith(ScheduleSlotRequestDTO... slots) {
    ScheduleModel schedule = new ScheduleModel();
    for (ScheduleSlotRequestDTO slot : slots) {
      schedule.addSlot(service.toSlot(slot));
    }
    return schedule;
  }
}
