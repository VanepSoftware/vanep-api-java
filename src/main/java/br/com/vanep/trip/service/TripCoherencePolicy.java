package br.com.vanep.trip.service;

import br.com.vanep.trip.enums.TripCoherenceViolation;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TripCoherencePolicy {

  public Optional<TripCoherenceViolation> validate(
      TripStatus status, Instant startedAt, Instant finishedAt) {
    return orderOfTimestamps(startedAt, finishedAt)
        .or(() -> timestampsExpectedBy(status, startedAt, finishedAt));
  }

  Optional<TripCoherenceViolation> orderOfTimestamps(Instant startedAt, Instant finishedAt) {
    if (finishedAt == null) {
      return Optional.empty();
    }
    if (startedAt == null) {
      return Optional.of(TripCoherenceViolation.STARTED_AT_REQUIRED);
    }
    if (finishedAt.isBefore(startedAt)) {
      return Optional.of(TripCoherenceViolation.FINISHED_BEFORE_STARTED);
    }
    return Optional.empty();
  }

  Optional<TripCoherenceViolation> timestampsExpectedBy(
      TripStatus status, Instant startedAt, Instant finishedAt) {
    return switch (status) {
      case SCHEDULED ->
          startedAt == null
              ? Optional.empty()
              : Optional.of(TripCoherenceViolation.STARTED_AT_FORBIDDEN);
      case IN_PROGRESS -> {
        if (startedAt == null) {
          yield Optional.of(TripCoherenceViolation.STARTED_AT_REQUIRED);
        }
        yield finishedAt == null
            ? Optional.empty()
            : Optional.of(TripCoherenceViolation.FINISHED_AT_FORBIDDEN);
      }
      case COMPLETED -> {
        if (startedAt == null) {
          yield Optional.of(TripCoherenceViolation.STARTED_AT_REQUIRED);
        }
        yield finishedAt == null
            ? Optional.of(TripCoherenceViolation.FINISHED_AT_REQUIRED)
            : Optional.empty();
      }
      case CANCELLED -> Optional.empty();
    };
  }
}
