package br.com.vanep.trip.enums;

public enum TripCoherenceViolation {
  STARTED_AT_REQUIRED("trip.coherence.started_at_required"),
  STARTED_AT_FORBIDDEN("trip.coherence.started_at_forbidden"),
  FINISHED_AT_REQUIRED("trip.coherence.finished_at_required"),
  FINISHED_AT_FORBIDDEN("trip.coherence.finished_at_forbidden"),
  FINISHED_BEFORE_STARTED("trip.coherence.finished_before_started");

  private final String messageKey;

  TripCoherenceViolation(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
