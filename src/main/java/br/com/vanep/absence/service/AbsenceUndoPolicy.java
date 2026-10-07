package br.com.vanep.absence.service;

import br.com.vanep.trip.enums.TripStatus;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AbsenceUndoPolicy {

  public boolean allows(TripStatus tripStatus) {
    return tripStatus == null || tripStatus == TripStatus.SCHEDULED;
  }

  public boolean allowsAll(List<TripStatus> tripStatuses) {
    if (tripStatuses == null || tripStatuses.isEmpty()) {
      return true;
    }
    for (TripStatus status : tripStatuses) {
      if (!allows(status)) {
        return false;
      }
    }
    return true;
  }
}
