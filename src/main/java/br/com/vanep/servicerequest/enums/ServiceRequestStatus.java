package br.com.vanep.servicerequest.enums;

import java.util.Set;

public enum ServiceRequestStatus {
  OPEN,
  ANSWERED,
  CONVERTED,
  DECLINED,
  EXPIRED,
  WITHDRAWN;

  public static final Set<ServiceRequestStatus> OPEN_STATUSES = Set.of(OPEN, ANSWERED);
}
