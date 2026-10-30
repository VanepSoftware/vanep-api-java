package br.com.vanep.servicerequest.service;

import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestTransitionPolicy {

  public boolean allows(ServiceRequestStatus from, ServiceRequestStatus to) {
    return nextStatusesOf(from).contains(to);
  }

  Set<ServiceRequestStatus> nextStatusesOf(ServiceRequestStatus from) {
    return switch (from) {
      case OPEN ->
          Set.of(
              ServiceRequestStatus.ANSWERED,
              ServiceRequestStatus.DECLINED,
              ServiceRequestStatus.WITHDRAWN,
              ServiceRequestStatus.EXPIRED);
      case ANSWERED ->
          Set.of(
              ServiceRequestStatus.OPEN,
              ServiceRequestStatus.CONVERTED,
              ServiceRequestStatus.DECLINED,
              ServiceRequestStatus.WITHDRAWN);
      case CONVERTED, DECLINED, EXPIRED, WITHDRAWN -> Set.of();
    };
  }
}
