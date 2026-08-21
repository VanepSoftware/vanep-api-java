package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record SchoolUpdateRequestDTO(
    JsonNullable<@Size(max = 255, message = "{school.name.too_long}") String> name,
    JsonNullable<@Pattern(regexp = "^\\d{14}$", message = "{school.cnpj.invalid}") String> cnpj,
    JsonNullable<@Size(max = 32, message = "{school.phone.too_long}") String> phone,
    JsonNullable<
            @Email(message = "{school.email.invalid}")
            @Size(max = 255, message = "{school.email.too_long}") String>
        email,
    JsonNullable<@Valid AddressRequestDTO> address) {

  public SchoolUpdateRequestDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (cnpj == null) {
      cnpj = JsonNullable.undefined();
    }
    if (phone == null) {
      phone = JsonNullable.undefined();
    }
    if (email == null) {
      email = JsonNullable.undefined();
    }
    if (address == null) {
      address = JsonNullable.undefined();
    }
  }
}
