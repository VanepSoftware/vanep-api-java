package br.com.vanep.schedule.service;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScheduleService {

  private final ScheduleRepository schedules;
  private final ScheduleSlotPolicy slotPolicy;
  private final MessageSource messages;

  public ScheduleService(
      ScheduleRepository schedules, ScheduleSlotPolicy slotPolicy, MessageSource messages) {
    this.schedules = schedules;
    this.slotPolicy = slotPolicy;
    this.messages = messages;
  }

  @Transactional
  public ScheduleModel create(List<ScheduleSlotRequestDTO> slots) {
    ensureValid(slots);
    ScheduleModel schedule = new ScheduleModel();
    addSlots(schedule, slots);
    return schedules.save(schedule);
  }

  @Transactional
  public ScheduleModel replace(ScheduleModel schedule, List<ScheduleSlotRequestDTO> slots) {
    ensureValid(slots);
    schedule.getSlots().clear();
    // Identity ids insert new slots before the orphan removal; flushing first frees the
    // (weekday, leg) held by the partial unique index.
    schedules.flush();
    addSlots(schedule, slots);
    return schedules.save(schedule);
  }

  void ensureValid(List<ScheduleSlotRequestDTO> slots) {
    slotPolicy
        .validate(slots)
        .ifPresent(
            violation -> {
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST,
                  messages.getMessage(
                      violation.messageKey(), null, LocaleContextHolder.getLocale()));
            });
  }

  void addSlots(ScheduleModel schedule, List<ScheduleSlotRequestDTO> slots) {
    slots.forEach(slot -> schedule.addSlot(toSlot(slot)));
  }

  ScheduleSlotModel toSlot(ScheduleSlotRequestDTO request) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(request.weekday());
    slot.setLeg(request.leg());
    slot.setShift(request.shift());
    slot.setWindowStart(request.windowStart());
    slot.setWindowEnd(request.windowEnd());
    return slot;
  }
}
