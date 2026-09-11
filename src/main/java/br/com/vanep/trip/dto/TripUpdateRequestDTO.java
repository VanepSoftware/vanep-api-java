package br.com.vanep.trip.dto;

import br.com.vanep.shared.enums.Shift;
import br.com.vanep.trip.enums.TripStatus;
import java.time.Instant;
import org.openapitools.jackson.nullable.JsonNullable;

public record TripUpdateRequestDTO(
    JsonNullable<Shift> shift,
    JsonNullable<TripStatus> status,
    JsonNullable<Instant> startedAt,
    JsonNullable<Instant> finishedAt) {

  public TripUpdateRequestDTO {
    if (shift == null) {
      shift = JsonNullable.undefined();
    }
    if (status == null) {
      status = JsonNullable.undefined();
    }
    if (startedAt == null) {
      startedAt = JsonNullable.undefined();
    }
    if (finishedAt == null) {
      finishedAt = JsonNullable.undefined();
    }
  }
}
