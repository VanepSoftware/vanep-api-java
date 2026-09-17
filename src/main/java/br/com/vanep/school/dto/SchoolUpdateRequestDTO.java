package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressRequestDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record SchoolUpdateRequestDTO(
    JsonNullable<@Size(max = 255, message = "{school.name.too_long}") String> name,
    JsonNullable<@Valid AddressRequestDTO> address) {
  public SchoolUpdateRequestDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (address == null) {
      address = JsonNullable.undefined();
    }
  }
}
