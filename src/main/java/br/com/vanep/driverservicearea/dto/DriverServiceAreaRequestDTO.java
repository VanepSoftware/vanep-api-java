package br.com.vanep.driverservicearea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corpo de {@code PUT /api/drivers/me/service-areas}. Substitui o conjunto inteiro.
 *
 * <p>Cada item traz o seu próprio {@code sessionToken} porque cada caixa de autocomplete tem uma
 * sessão própria no Places (D5).
 */
public record DriverServiceAreaRequestDTO(
    @NotEmpty @Size(max = MAX_AREAS, message = "{driver_service_area.areas.too_many}") @Valid
        List<Item> areas) {

  /**
   * Teto de regiões por motorista, compartilhado com o app.
   *
   * <p>Sem ele, cada item da lista é um {@code Place Details} pago disparado dentro da mesma
   * requisição, e o {@code sessionToken} ainda fura o cache (D5): um motorista autenticado mandando
   * 200 ids viram 200 chamadas ao Google. O rate limit global cobre login e signup, não este PUT.
   *
   * <p>Um teto só no cliente não é teto: quem chama a API direto ignora, e cada área é uma linha
   * numa tabela compartilhada.
   */
  public static final int MAX_AREAS = 10;

  public record Item(
      @NotBlank @Size(max = 255) String placeId, @Size(max = 255) String sessionToken) {}
}
