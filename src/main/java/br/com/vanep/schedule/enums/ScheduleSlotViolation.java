package br.com.vanep.schedule.enums;

public enum ScheduleSlotViolation {
  SLOTS_REQUIRED("schedule.slots.required"),
  WINDOW_INVALID("schedule.slot.window_invalid"),
  SLOT_DUPLICATE("schedule.slot.duplicate");

  private final String messageKey;

  ScheduleSlotViolation(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
