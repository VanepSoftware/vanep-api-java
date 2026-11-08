package br.com.vanep.unlinkedpassenger.dto;

import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.shared.enums.SchoolShift;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record UnlinkedPassengerUpdateRequestDTO(
    JsonNullable<@Size(max = 255, message = "{unlinked_passenger.name.too_long}") String> name,
    JsonNullable<String> schoolToken,
    JsonNullable<SchoolShift> schoolShift,
    JsonNullable<@Valid DependentAddressRequestDTO> address,
    JsonNullable<@Size(max = 500, message = "{unlinked_passenger.notes.too_long}") String> notes) {

  public UnlinkedPassengerUpdateRequestDTO {
    if (name == null) {
      name = JsonNullable.undefined();
    }
    if (schoolToken == null) {
      schoolToken = JsonNullable.undefined();
    }
    if (schoolShift == null) {
      schoolShift = JsonNullable.undefined();
    }
    if (address == null) {
      address = JsonNullable.undefined();
    }
    if (notes == null) {
      notes = JsonNullable.undefined();
    }
  }
}
