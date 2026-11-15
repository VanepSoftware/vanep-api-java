package br.com.vanep.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestItemsViolation;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceRequestItemsPolicyTest {

  private final ServiceRequestItemsPolicy policy = new ServiceRequestItemsPolicy();

  @Test
  void acceptsSiblingsAskingForTheSameWeekdaysWithOneLegOrBoth() {
    assertThat(
            policy.validate(
                List.of(
                    item(
                        "lucas",
                        day(DayOfWeek.MONDAY, true, true),
                        day(DayOfWeek.TUESDAY, true, false)),
                    item("ana", day(DayOfWeek.MONDAY, false, true)))))
        .isEmpty();
  }

  @Test
  void refusesTheSameDependentTwice() {
    assertThat(
            policy.validate(
                List.of(
                    item("lucas", day(DayOfWeek.MONDAY, true, true)),
                    item("lucas", day(DayOfWeek.TUESDAY, true, true)))))
        .contains(ServiceRequestItemsViolation.DEPENDENT_DUPLICATE);
  }

  @Test
  void refusesTheSameWeekdayTwiceInOneItem() {
    assertThat(
            policy.validate(
                List.of(
                    item(
                        "lucas",
                        day(DayOfWeek.MONDAY, true, false),
                        day(DayOfWeek.MONDAY, false, true)))))
        .contains(ServiceRequestItemsViolation.WEEKDAY_DUPLICATE);
  }

  @Test
  void refusesADayThatNeedsNoLeg() {
    assertThat(
            policy.validate(
                List.of(
                    item("lucas", day(DayOfWeek.MONDAY, true, true)),
                    item("ana", day(DayOfWeek.WEDNESDAY, false, false)))))
        .contains(ServiceRequestItemsViolation.DAY_WITHOUT_LEG);
  }

  private ServiceRequestItemRequestDTO item(
      String dependentToken, ServiceRequestDayRequestDTO... days) {
    return new ServiceRequestItemRequestDTO(dependentToken, List.of(days));
  }

  private ServiceRequestDayRequestDTO day(
      DayOfWeek weekday, boolean needsOutbound, boolean needsReturn) {
    return new ServiceRequestDayRequestDTO(weekday, needsOutbound, needsReturn);
  }
}
