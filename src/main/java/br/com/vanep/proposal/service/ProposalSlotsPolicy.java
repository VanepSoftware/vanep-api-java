package br.com.vanep.proposal.service;

import br.com.vanep.proposal.dto.ProposalItemRequestDTO;
import br.com.vanep.schedule.service.ScheduleSlotPolicy.WeekdayLeg;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.RouteLeg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ProposalSlotsPolicy {

  public boolean offersOnlyRequestedLegs(
      ServiceRequestModel request,
      Map<String, Long> dependentIdsByToken,
      List<ProposalItemRequestDTO> items) {
    Map<Long, Set<WeekdayLeg>> requested =
        request.getItems().stream()
            .collect(
                Collectors.toMap(item -> item.getDependentId(), item -> requestedLegsOf(item)));
    return items.stream()
        .allMatch(
            item ->
                requested
                    .getOrDefault(dependentIdsByToken.get(item.dependentToken()), Set.of())
                    .containsAll(offeredLegsOf(item)));
  }

  Set<WeekdayLeg> requestedLegsOf(ServiceRequestItemModel item) {
    return item.getDays().stream().flatMap(day -> legsOf(day).stream()).collect(Collectors.toSet());
  }

  List<WeekdayLeg> legsOf(ServiceRequestDayModel day) {
    List<WeekdayLeg> legs = new ArrayList<>();
    if (day.isNeedsOutbound()) {
      legs.add(new WeekdayLeg(day.getWeekday(), RouteLeg.OUTBOUND));
    }
    if (day.isNeedsReturn()) {
      legs.add(new WeekdayLeg(day.getWeekday(), RouteLeg.RETURN));
    }
    return legs;
  }

  Set<WeekdayLeg> offeredLegsOf(ProposalItemRequestDTO item) {
    return item.slots().stream()
        .map(slot -> new WeekdayLeg(slot.weekday(), slot.leg()))
        .collect(Collectors.toSet());
  }
}
