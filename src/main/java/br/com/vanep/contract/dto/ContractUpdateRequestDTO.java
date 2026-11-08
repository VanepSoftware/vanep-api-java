package br.com.vanep.contract.dto;

import br.com.vanep.contract.enums.ContractStatus;
import org.openapitools.jackson.nullable.JsonNullable;

public record ContractUpdateRequestDTO(JsonNullable<ContractStatus> status) {

  public ContractUpdateRequestDTO {
    if (status == null) {
      status = JsonNullable.undefined();
    }
  }
}
