package br.com.vanep.schedule.service;

import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.schedule.enums.ScheduleSlotViolation;
import br.com.vanep.schedule.model.ScheduleModel;
import br.com.vanep.schedule.model.ScheduleSlotModel;
import br.com.vanep.schedule.repository.ScheduleRepository;
import br.com.vanep.shared.error.ApiProblemException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

  private final ScheduleRepository schedules;
  private final ScheduleSlotPolicy slotPolicy;

  public ScheduleService(ScheduleRepository schedules, ScheduleSlotPolicy slotPolicy) {
    this.schedules = schedules;
    this.slotPolicy = slotPolicy;
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
              throw ApiProblemException.badRequest(messageKey(violation));
            });
  }

  String messageKey(ScheduleSlotViolation violation) {
    return switch (violation) {
      case SLOTS_REQUIRED -> "schedule.slots.required";
      case SLOT_DUPLICATE -> "schedule.slot.duplicate";
    };
  }

  void addSlots(ScheduleModel schedule, List<ScheduleSlotRequestDTO> slots) {
    slots.forEach(slot -> schedule.addSlot(toSlot(slot)));
  }

  ScheduleSlotModel toSlot(ScheduleSlotRequestDTO request) {
    ScheduleSlotModel slot = new ScheduleSlotModel();
    slot.setWeekday(request.weekday());
    slot.setLeg(request.leg());
    slot.setShift(request.shift());
    slot.setPickupTime(request.pickupTime());
    return slot;
  }
}
