package br.com.vanep.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.openapitools.jackson.nullable.JsonNullable;

public record ClientUpdateRequestDTO(
    JsonNullable<@Size(max = 255) String> name,
    JsonNullable<@Email @Size(max = 255) String> email,
    JsonNullable<BigDecimal> rating,
    JsonNullable<Boolean> active) {

  public ClientUpdateRequestDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (email == null) {
      email = JsonNullable.undefined();
    }
    if (rating == null) {
      rating = JsonNullable.undefined();
    }
    if (active == null) {
      active = JsonNullable.undefined();
    }
  }
}
