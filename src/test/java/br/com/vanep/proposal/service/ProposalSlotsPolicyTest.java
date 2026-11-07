package br.com.vanep.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.schedule.dto.ScheduleSlotRequestDTO;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.OperationShift;
import br.com.vanep.shared.enums.RouteLeg;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProposalSlotsPolicyTest {

  private static final Map<String, Long> IDS = Map.of("lucas", 21L, "marina", 22L);

  private final ProposalSlotsPolicy policy = new ProposalSlotsPolicy();

  private ServiceRequestModel request;

  @BeforeEach
  void setUp() {
    request = new ServiceRequestModel();
    request.addItem(
        requestItem(21L, day(DayOfWeek.MONDAY, true, true), day(DayOfWeek.TUESDAY, true, false)));
    request.addItem(requestItem(22L, day(DayOfWeek.MONDAY, true, true)));
  }

  @Test
  void everyRequestedLegIsAccepted() {
    assertThat(
            policy.offersOnlyRequestedLegs(
                request,
                IDS,
                List.of(
                    item(
                        "lucas",
                        slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND),
                        slot(DayOfWeek.MONDAY, RouteLeg.RETURN),
                        slot(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND)),
                    item(
                        "marina",
                        slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND),
                        slot(DayOfWeek.MONDAY, RouteLeg.RETURN)))))
        .isTrue();
  }

  @Test
  void fewerLegsThanRequestedAreAccepted() {
    assertThat(
            policy.offersOnlyRequestedLegs(
                request,
                IDS,
                List.of(
                    item("lucas", slot(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND)),
                    item("marina", slot(DayOfWeek.MONDAY, RouteLeg.RETURN)))))
        .isTrue();
  }

  @Test
  void aWeekdayThatWasNotRequestedIsRefused() {
    assertThat(
            policy.offersOnlyRequestedLegs(
                request,
                IDS,
                List.of(
                    item("lucas", slot(DayOfWeek.SATURDAY, RouteLeg.OUTBOUND)),
                    item("marina", slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND)))))
        .isFalse();
  }

  @Test
  void theReturnOnADayWithOnlyTheOutboundRequestedIsRefused() {
    assertThat(
            policy.offersOnlyRequestedLegs(
                request,
                IDS,
                List.of(
                    item("lucas", slot(DayOfWeek.TUESDAY, RouteLeg.RETURN)),
                    item("marina", slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND)))))
        .isFalse();
  }

  @Test
  void aLegRequestedForAnotherDependentIsRefused() {
    assertThat(
            policy.offersOnlyRequestedLegs(
                request,
                IDS,
                List.of(
                    item("lucas", slot(DayOfWeek.MONDAY, RouteLeg.OUTBOUND)),
                    item("marina", slot(DayOfWeek.TUESDAY, RouteLeg.OUTBOUND)))))
        .isFalse();
  }

  private ServiceRequestItemModel requestItem(Long dependentId, ServiceRequestDayModel... days) {
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(dependentId);
    for (ServiceRequestDayModel day : days) {
      item.addDay(day);
    }
    return item;
  }

  private ServiceRequestDayModel day(
      DayOfWeek weekday, boolean needsOutbound, boolean needsReturn) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(weekday);
    day.setNeedsOutbound(needsOutbound);
    day.setNeedsReturn(needsReturn);
    return day;
  }

  private ProposalItemRequestDTO item(String dependentToken, ScheduleSlotRequestDTO... slots) {
    return new ProposalItemRequestDTO(dependentToken, List.of(slots));
  }

  private ScheduleSlotRequestDTO slot(DayOfWeek weekday, RouteLeg leg) {
    return new ScheduleSlotRequestDTO(weekday, leg, OperationShift.MORNING, LocalTime.of(6, 40));
  }
}
