package br.com.vanep.driverservicearea.service;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;

/**
 * Decide se uma região declarada pelo motorista é específica o bastante (D8).
 *
 * <p>O problema que ela resolve: no DF o nível cidade é inútil. O Distrito Federal tem um único
 * município, então "Brasília" declara 5.800 km², do Gama a Sobradinho. Cidades pequenas, ao
 * contrário, precisam poder declarar a cidade inteira.
 *
 * <p><b>A regra não olha o banco.</b> Ela recebe a cadeia já resolvida e os flags curados que vêm
 * carregados nela. A formulação anterior — "exigir distrito quando a cidade já tem distritos
 * cadastrados" — dependia de estado mutável: o mesmo cadastro seria aceito ou rejeitado conforme o
 * relógio, e os primeiros motoristas da praça de lançamento cairiam todos no caso furado, que é
 * justamente onde a regra precisava valer.
 *
 * <p>Pura de propósito: sem servlet, sem JPA, sem query. Testável preparando um objeto, não um
 * banco.
 */
public final class ServiceAreaGranularityPolicy {

  private ServiceAreaGranularityPolicy() {}

  /**
   * @return {@code true} quando a cadeia é aceitável como área de atuação
   */
  public static boolean isAcceptable(ResolvedLocationChainDTO chain) {
    if (chain.hasDistrictComponent()) {
      return true;
    }
    return !requiresDistrict(chain.city());
  }

  /**
   * {@code COALESCE(city.requires_district, city.state.requires_district)}.
   *
   * <p>Lido através da cadeia já carregada, sem query extra: o resolver atravessa {@code country →
   * state → city} para montá-la, então o estado já está em memória. O flag também não é copiado
   * para a linha de {@code city} na criação — copiar reintroduziria o mesmo congelamento temporal
   * que motivou esta decisão.
   */
  public static boolean requiresDistrict(CityModel city) {
    if (city.getRequiresDistrict() != null) {
      return city.getRequiresDistrict();
    }
    return city.getState().isRequiresDistrict();
  }
}
