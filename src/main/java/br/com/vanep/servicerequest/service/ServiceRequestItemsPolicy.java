package br.com.vanep.servicerequest.service;

import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestItemRequestDTO;
import br.com.vanep.servicerequest.enums.ServiceRequestItemsViolation;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestItemsPolicy {

  public Optional<ServiceRequestItemsViolation> validate(List<ServiceRequestItemRequestDTO> items) {
    if (hasRepeatedDependent(items)) {
      return Optional.of(ServiceRequestItemsViolation.DEPENDENT_DUPLICATE);
    }
    if (items.stream().anyMatch(item -> hasRepeatedWeekday(item.days()))) {
      return Optional.of(ServiceRequestItemsViolation.WEEKDAY_DUPLICATE);
    }
    if (items.stream().flatMap(item -> item.days().stream()).anyMatch(day -> !needsAnyLeg(day))) {
      return Optional.of(ServiceRequestItemsViolation.DAY_WITHOUT_LEG);
    }
    return Optional.empty();
  }

  boolean hasRepeatedDependent(List<ServiceRequestItemRequestDTO> items) {
    return items.stream().map(item -> item.dependentToken()).distinct().count() < items.size();
  }

  boolean hasRepeatedWeekday(List<ServiceRequestDayRequestDTO> days) {
    return days.stream().map(day -> day.weekday()).distinct().count() < days.size();
  }

  boolean needsAnyLeg(ServiceRequestDayRequestDTO day) {
    return Boolean.TRUE.equals(day.needsOutbound()) || Boolean.TRUE.equals(day.needsReturn());
  }
}
