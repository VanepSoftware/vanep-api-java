package br.com.vanep.clientdriver.dto;

import br.com.vanep.clientdriver.enums.RelationshipStatus;
import org.openapitools.jackson.nullable.JsonNullable;

public record ClientDriverUpdateRequestDTO(JsonNullable<RelationshipStatus> status) {

  public ClientDriverUpdateRequestDTO {
    if (status == null) {
      status = JsonNullable.undefined();
    }
  }
}
