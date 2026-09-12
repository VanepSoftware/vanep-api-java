package br.com.vanep.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkWindowPolicyTest {

  private static final LocalTime SEVEN = LocalTime.of(7, 0);
  private static final LocalTime EIGHTEEN = LocalTime.of(18, 0);
  private static final List<String> WEEKDAYS =
      List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");

  private final WorkWindowPolicy policy = new WorkWindowPolicy();

  private static LocalDateTime thursdayAt(int hour, int minute) {
    return LocalDateTime.of(2026, 9, 10, hour, minute);
  }

  private static LocalDateTime saturdayAt(int hour, int minute) {
    return LocalDateTime.of(2026, 9, 12, hour, minute);
  }

  @Test
  void insideTheWindowIsNotOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, SEVEN, EIGHTEEN, thursdayAt(7, 30))).isFalse();
  }

  @Test
  void beforeTheStartTimeIsOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, SEVEN, EIGHTEEN, thursdayAt(6, 41))).isTrue();
  }

  @Test
  void afterTheEndTimeIsOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, SEVEN, EIGHTEEN, thursdayAt(19, 5))).isTrue();
  }

  @Test
  void aDayNotListedIsOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, SEVEN, EIGHTEEN, saturdayAt(7, 30))).isTrue();
  }

  @Test
  void nullWorkDaysIsNotEvidenceOfBeingOutside() {
    assertThat(policy.isOutsideWorkWindow(null, null, null, thursdayAt(3, 0))).isFalse();
  }

  @Test
  void emptyWorkDaysIsNotEvidenceOfBeingOutside() {
    assertThat(policy.isOutsideWorkWindow(List.of(), null, null, saturdayAt(23, 0))).isFalse();
  }

  @Test
  void unparseableWorkDaysIsNotEvidenceOfBeingOutside() {
    assertThat(policy.isOutsideWorkWindow(List.of("seg", "qua"), null, null, saturdayAt(9, 0)))
        .isFalse();
  }

  @Test
  void nullStartTimeDoesNotMakeTheMorningOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, null, EIGHTEEN, thursdayAt(4, 0))).isFalse();
  }

  @Test
  void lowercaseDayNamesAreRecognized() {
    assertThat(policy.isOutsideWorkWindow(List.of("thursday"), null, null, thursdayAt(9, 0)))
        .isFalse();
    assertThat(policy.isOutsideWorkWindow(List.of("thursday"), null, null, saturdayAt(9, 0)))
        .isTrue();
  }

  @Test
  void aNullStartInstantIsNotOutside() {
    assertThat(policy.isOutsideWorkWindow(WEEKDAYS, SEVEN, EIGHTEEN, null)).isFalse();
  }
}
