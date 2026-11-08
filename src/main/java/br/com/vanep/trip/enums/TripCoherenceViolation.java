package br.com.vanep.trip.enums;

public enum TripCoherenceViolation {
  STARTED_AT_REQUIRED,
  STARTED_AT_FORBIDDEN,
  FINISHED_AT_REQUIRED,
  FINISHED_AT_FORBIDDEN,
  FINISHED_BEFORE_STARTED
}
