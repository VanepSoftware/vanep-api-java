package br.com.vanep.driverservicearea.repository;

import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverServiceAreaRepository extends JpaRepository<DriverServiceAreaModel, Long> {

  @Query(
      """
      select area from DriverServiceAreaModel area
      join fetch area.city city
      join fetch city.state state
      join fetch state.country
      left join fetch area.district
      where area.driver.id = :driverId
      """)
  List<DriverServiceAreaModel> findByDriverId(Long driverId);

  /**
   * Motoristas que cobrem um ponto ancorado em distrito (D4).
   *
   * <p>Casa quando a área é a cidade inteira ({@code district_id} nulo) ou quando o distrito da
   * área é a âncora do ponto ou um de seus ancestrais. É por isso que quem cadastrou "Taguatinga"
   * aparece numa busca por "QNL 5 Conjunto J": Taguatinga está na lista de ancestrais.
   *
   * <p>São joins e {@code IN} comuns — sem PostGIS, sem geometria, sem materialized path. Roda em
   * H2, o que mantém a suíte sem Testcontainers.
   *
   * <p>O {@code left join} é obrigatório e não é estilo: escrever {@code area.district.id} faria o
   * JPQL montar um <b>inner join</b> implícito, que descarta as linhas de {@code district_id} nulo
   * antes de o {@code or} ser avaliado. O efeito seria sumir da busca exatamente quem cadastrou a
   * cidade inteira — o caso mais abrangente, e em silêncio.
   */
  @Query(
      """
      select distinct area.driver.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
        and (district is null or district.id in :ancestorIds)
      """)
  List<Long> findDriverIdsCoveringPoint(Long cityId, Collection<Long> ancestorIds);

  /**
   * O mesmo match da contenção, mas devolvendo <b>qual</b> distrito casou.
   *
   * <p>O ranking precisa disso: a posição desse distrito na lista de ancestrais do ponto é a
   * distância entre a área do motorista e o lugar buscado. Sem o distrito de volta, só daria para
   * dizer que casou, não o quão perto.
   *
   * <p>Cada linha é {@code [driverId, districtId]}, com {@code districtId} nulo quando a área é a
   * cidade inteira.
   */
  @Query(
      """
      select area.driver.id, district.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
        and (district is null or district.id in :ancestorIds)
      """)
  List<Object[]> findDriverMatchesCoveringPoint(Long cityId, Collection<Long> ancestorIds);

  @Query(
      """
      select area.driver.id, district.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
      """)
  List<Object[]> findDriverMatchesInCity(Long cityId);

  /**
   * Busca ampla: a âncora parou na cidade, então todo motorista da cidade serve — inclusive quem
   * declarou apenas um distrito. Excluí-los faria uma busca mais genérica devolver menos resultados
   * que uma específica, que é o contrário do que o usuário espera.
   */
  @Query(
      """
      select distinct area.driver.id from DriverServiceAreaModel area
      where area.city.id = :cityId
      """)
  List<Long> findDriverIdsInCity(Long cityId);
}
