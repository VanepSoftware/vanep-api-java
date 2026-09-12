package br.com.vanep.trip.service;

import br.com.vanep.trip.enums.TripStatus;
import org.springframework.stereotype.Component;

@Component
public class TripTransitionPolicy {

  public boolean canStart(TripStatus current) {
    return current == TripStatus.SCHEDULED;
  }

  public boolean canFinish(TripStatus current) {
    return current == TripStatus.IN_PROGRESS;
  }

  public boolean allows(TripStatus from, TripStatus to) {
    return switch (to) {
      case IN_PROGRESS -> canStart(from);
      case COMPLETED -> canFinish(from);
      default -> false;
    };
  }
}
