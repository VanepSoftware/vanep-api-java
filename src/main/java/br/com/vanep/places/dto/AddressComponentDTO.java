package br.com.vanep.places.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Um componente de endereço devolvido pelo Google Places.
 *
 * <p>{@code types} pode vir ausente — a fixture {@code df-escola-objetivo} traz um componente sem o
 * campo (ver R1). Por isso o acessor normaliza para lista vazia em vez de devolver {@code null}: um
 * {@code NullPointerException} no resolver seria indistinguível de um erro de mapeamento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressComponentDTO(String longText, String shortText, List<String> types) {

  public AddressComponentDTO {
    types = types == null ? List.of() : List.copyOf(types);
  }

  public boolean hasNoTypes() {
    return types.isEmpty();
  }
}
