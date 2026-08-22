package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.dependent.enums.Shift;
import br.com.vanep.user.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record DependentUpdateDTO(
    JsonNullable<@Size(max = 255, message = "{dependent.name.too_long}") String> name,
    JsonNullable<LocalDate> birthDate,
    JsonNullable<Gender> gender,
    JsonNullable<@Size(max = 64, message = "{dependent.document.too_long}") String> document,
    JsonNullable<@Size(max = 32, message = "{dependent.phone.too_long}") String> phone,
    JsonNullable<
            @Email(message = "{user.profile.email.invalid}")
            @Size(max = 255, message = "{user.profile.email.too_long}") String>
        email,
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
