package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.enums.Gender;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record DependentUpdateDTO(
    JsonNullable<String> name,
    JsonNullable<LocalDate> birthDate,
    JsonNullable<Gender> gender,
    JsonNullable<String> document,
    JsonNullable<String> phone,
    JsonNullable<String> email,
    JsonNullable<Boolean> isSelf,
    JsonNullable<Boolean> isDefault,
    JsonNullable<Shift> shift,
    JsonNullable<@Valid AddressRequestDTO> address,
    JsonNullable<String> schoolToken) {

  public DependentUpdateDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (birthDate == null) {
      birthDate = JsonNullable.undefined();
    }
    if (gender == null) {
      gender = JsonNullable.undefined();
    }
    if (document == null) {
      document = JsonNullable.undefined();
    }
    if (phone == null) {
      phone = JsonNullable.undefined();
    }
    if (email == null) {
      email = JsonNullable.undefined();
    }
    if (isSelf == null) {
      isSelf = JsonNullable.undefined();
    }
    if (isDefault == null) {
      isDefault = JsonNullable.undefined();
    }
    if (shift == null) {
      shift = JsonNullable.undefined();
    }
    if (address == null) {
      address = JsonNullable.undefined();
    }
    if (schoolToken == null) {
      schoolToken = JsonNullable.undefined();
    }
  }
}
