package br.com.vanep.driver.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um motorista no resultado da busca.
 *
 * <p><b>Nenhum campo de endereço residencial aparece aqui</b> — nem rua, nem número, nem CEP, nem
 * complemento, nem o bairro onde o motorista mora. O que a busca casou foi a <i>área de atuação</i>
 * (dado público), e a residência do motorista não tem relação com isso.
 *
 * <p>Requisito de privacidade da spec `personal-address`. A garantia começa no schema — {@code
 * driver_service_area} não tem coluna de logradouro — e este DTO é a segunda barreira.
 *
 * <p>{@code serviceAreas} traz os nomes das regiões atendidas, do mais específico para o mais
 * amplo. É dado público por construção: são nós da árvore, sem logradouro.
 */
public record DriverSearchResponseDTO(
    String token,
    String name,
    String photo,
    BigDecimal rating,
    BigDecimal basePrice,
    Integer experienceYears,
    boolean available,
    List<String> serviceAreas) {

  public DriverSearchResponseDTO {
    serviceAreas = serviceAreas == null ? List.of() : List.copyOf(serviceAreas);
  }
}
