package br.com.vanep.cep.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ViaCepResponseDTO(
    String cep,
    @JsonProperty("logradouro") String street,
    @JsonProperty("bairro") String neighborhood,
    @JsonProperty("localidade") String cityName,
    String uf,
    String ibge,
    Object erro) {

  public boolean isError() {
    if (erro == null) {
      return false;
    }
    if (erro instanceof Boolean flag) {
      return flag;
    }
    return "true".equalsIgnoreCase(erro.toString());
  }
}
