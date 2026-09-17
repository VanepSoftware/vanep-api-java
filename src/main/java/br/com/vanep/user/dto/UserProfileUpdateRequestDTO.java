package br.com.vanep.user.dto;

import br.com.vanep.user.enums.Gender;
import org.openapitools.jackson.nullable.JsonNullable;

public record UserProfileUpdateRequestDTO(
    JsonNullable<String> name, JsonNullable<String> phone, JsonNullable<Gender> gender) {

  public UserProfileUpdateRequestDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (phone == null) {
      phone = JsonNullable.undefined();
    }
    if (gender == null) {
      gender = JsonNullable.undefined();
    }
  }
}
