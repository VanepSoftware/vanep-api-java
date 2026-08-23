package br.com.vanep.location.dto;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.state.model.StateModel;
import java.util.List;
import java.util.Optional;

/**
 * A cadeia da árvore correspondente a um place.
 *
 * <p>{@code districts} vai do raso para o fundo e pode estar vazia. Em resolução de âncora
 * (read-only) ela pode ser mais curta que os componentes do place: para no nó mais profundo que já
 * existe.
 *
 * <p>{@code hasDistrictComponent} é lido dos <b>componentes do Google</b>, não da árvore. É a
 * entrada da validação D8 na fase 6, que não pode depender do estado do banco — se dependesse, o
 * mesmo cadastro seria aceito ou rejeitado conforme o relógio.
 */
public record ResolvedLocationChainDTO(
    CountryModel country,
    StateModel state,
    CityModel city,
    List<DistrictModel> districts,
    boolean hasDistrictComponent) {

  public ResolvedLocationChainDTO {
    districts = districts == null ? List.of() : List.copyOf(districts);
  }

  /**
   * O nó mais profundo resolvido: o distrito mais fundo, ou vazio quando a cadeia para na cidade.
   */
  public Optional<DistrictModel> deepestDistrict() {
    return districts.isEmpty()
        ? Optional.empty()
        : Optional.of(districts.get(districts.size() - 1));
  }

  /**
   * Verdadeiro quando o place trouxe componente de distrito mas a árvore ainda não tem nenhum dele.
   * Só acontece em resolução de âncora — {@code resolveAndPersist} sempre cria a cadeia inteira.
   */
  public boolean anchoredAboveTheDistrictComponents() {
    return hasDistrictComponent && districts.isEmpty();
  }
}
