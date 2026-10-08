package br.com.vanep.stopchange.service;

import br.com.vanep.stopchange.enums.StopChangeStatus;
import org.springframework.stereotype.Component;

@Component
public class StopChangeTransitionPolicy {

  public boolean canApprove(StopChangeStatus current) {
    return current == StopChangeStatus.PENDING;
  }

  public boolean canReject(StopChangeStatus current) {
    return current == StopChangeStatus.PENDING;
  }

  public boolean canCancel(StopChangeStatus current) {
    return current == StopChangeStatus.PENDING;
  }

  public boolean canExpire(StopChangeStatus current) {
    return current == StopChangeStatus.PENDING;
  }

  public boolean allows(StopChangeStatus from, StopChangeStatus to) {
    return switch (to) {
      case APPROVED -> canApprove(from);
      case REJECTED -> canReject(from);
      case CANCELLED -> canCancel(from);
      case EXPIRED -> canExpire(from);
      default -> false;
    };
  }
}
