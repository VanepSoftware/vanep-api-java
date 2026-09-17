package br.com.vanep.trip.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WorkWindowPolicy {

  public boolean isOutsideWorkWindow(
      List<String> workDays, LocalTime workStart, LocalTime workEnd, LocalDateTime startedAt) {
    if (startedAt == null) {
      return false;
    }
    return isOutsideWorkDays(workDays, startedAt.getDayOfWeek())
        || isOutsideWorkHours(workStart, workEnd, startedAt.toLocalTime());
  }

  boolean isOutsideWorkDays(List<String> workDays, DayOfWeek day) {
    if (workDays == null || workDays.isEmpty()) {
      return false;
    }
    boolean anyRecognized = workDays.stream().anyMatch(this::isRecognizedDay);
    if (!anyRecognized) {
      return false;
    }
    return workDays.stream().noneMatch(configured -> matchesDay(configured, day));
  }

  boolean isOutsideWorkHours(LocalTime workStart, LocalTime workEnd, LocalTime time) {
    if (workStart != null && time.isBefore(workStart)) {
      return true;
    }
    return workEnd != null && time.isAfter(workEnd);
  }

  private boolean isRecognizedDay(String configured) {
    return parse(configured) != null;
  }

  private boolean matchesDay(String configured, DayOfWeek day) {
    return parse(configured) == day;
  }

  private DayOfWeek parse(String configured) {
    if (configured == null || configured.isBlank()) {
      return null;
    }
    try {
      return DayOfWeek.valueOf(configured.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException notADayName) {
      return null;
    }
  }
}
