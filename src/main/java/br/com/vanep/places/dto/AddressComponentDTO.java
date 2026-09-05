package br.com.vanep.places.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressComponentDTO(String longText, String shortText, List<String> types) {
  public AddressComponentDTO {
    types = types == null ? List.of() : List.copyOf(types);
  }

  public boolean hasNoTypes() {
    return types.isEmpty();
  }
}
