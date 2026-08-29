package br.com.vanep.driver.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverSearchResponseDTO;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Busca motoristas que atendem <b>um</b> lugar, ordenados por quão específica é a cobertura.
 *
 * <p>Read-only por construção (D3): usa {@code resolveAnchor}, que nunca escreve. Se a busca
 * criasse nós, a árvore cresceria com termos de busca e um {@code GET} que escreve seria
 * pré-buscável por intermediários.
 */
@Service
public class DriverSearchService {

  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final DriverServiceAreaRepository areas;
  private final DistrictRepository districts;
  private final DriverRepository drivers;
  private final RateLimiter rateLimiter;
  private final MessageSource messages;

  public DriverSearchService(
      PlacesClient places,
      LocationResolverService resolver,
      DriverServiceAreaRepository areas,
      DistrictRepository districts,
      DriverRepository drivers,
      @Qualifier("placesRateLimiter") RateLimiter rateLimiter,
      MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.areas = areas;
    this.districts = districts;
    this.drivers = drivers;
    this.rateLimiter = rateLimiter;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public Page<DriverSearchResponseDTO> search(
      String callerUid, String placeId, String sessionToken, Pageable pageable) {

    // Antes de qualquer chamada paga: o placeId vem do cliente (R6).
    if (!rateLimiter.tryAcquire("driver-search:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    Optional<ResolvedLocationChainDTO> anchor =
        resolver.resolveAnchor(places.findPlaceDetails(placeId, sessionToken));

    // Âncora vazia significa que nem a cidade existe na árvore. Ninguém pode ter
    // cadastrado ali, então o resultado é vazio — não é erro.
    if (anchor.isEmpty()) {
      return Page.empty(pageable);
    }

    List<Long> ranked = rankDriverIds(anchor.get());
    if (ranked.isEmpty()) {
      return Page.empty(pageable);
    }
    return pageInRankOrder(ranked, pageable);
  }

  /**
   * Ids de motorista do mais específico para o mais amplo.
   *
   * <p>O rank de uma área é a posição do distrito dela na lista de ancestrais do ponto: 0 é o
   * próprio nó buscado, 1 o pai, e assim por diante. Área de cidade inteira recebe o rank seguinte
   * ao último ancestral, de modo que sempre ordena por último — presente, mas fora de prioridade.
   *
   * <p>Um motorista com várias áreas casando fica com o <b>melhor</b> rank: quem cadastrou QNL 5 e
   * também Brasília inteira merece a posição de quem cadastrou QNL 5.
   */
  List<Long> rankDriverIds(ResolvedLocationChainDTO anchor) {
    Map<Long, Integer> distanceByDistrict = distanceByDistrict(anchor);
    int wholeCityRank = Integer.MAX_VALUE;

    List<Object[]> matches =
        anchor.deepestDistrict().isEmpty() && !anchor.anchoredAboveTheDistrictComponents()
            ? areas.findDriverMatchesInCity(anchor.city().getId())
            : areas.findDriverMatchesCoveringPoint(
                anchor.city().getId(), sentinelIfEmpty(List.copyOf(distanceByDistrict.keySet())));

    Map<Long, Integer> bestRankByDriver = new HashMap<>();
    for (Object[] match : matches) {
      Long driverId = (Long) match[0];
      Long districtId = (Long) match[1];
      Integer distance = districtId == null ? wholeCityRank : distanceByDistrict.get(districtId);
      if (distance == null) {
        continue;
      }
      bestRankByDriver.merge(driverId, distance, Math::min);
    }

    // Desempate por id para a ordem ser determinística entre requisições iguais.
    return bestRankByDriver.entrySet().stream()
        .sorted(
            Map.Entry.<Long, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * Distância em níveis entre cada distrito relevante e o ponto buscado: o próprio nó é 0, os
   * ancestrais sobem, os descendentes descem. Distritos fora do ramo não entram no mapa e por isso
   * não casam — um irmão não atende o ponto.
   */
  Map<Long, Integer> distanceByDistrict(ResolvedLocationChainDTO anchor) {
    Map<Long, Integer> distances = new HashMap<>();
    Optional<DistrictModel> deepest = anchor.deepestDistrict();
    if (deepest.isEmpty()) {
      return distances;
    }

    List<DistrictModel> ancestors = resolver.findAncestors(deepest.get());
    for (int level = 0; level < ancestors.size(); level++) {
      distances.put(ancestors.get(level).getId(), level);
    }

    Map<Long, List<DistrictModel>> childrenByParent = new HashMap<>();
    for (DistrictModel district : districts.findByCityId(anchor.city().getId())) {
      Long parentId = district.getParent() == null ? null : district.getParent().getId();
      childrenByParent.computeIfAbsent(parentId, id -> new ArrayList<>()).add(district);
    }
    collectDescendants(deepest.get().getId(), 1, childrenByParent, distances);
    return distances;
  }

  void collectDescendants(
      Long parentId,
      int distance,
      Map<Long, List<DistrictModel>> childrenByParent,
      Map<Long, Integer> distances) {
    for (DistrictModel child : childrenByParent.getOrDefault(parentId, List.of())) {
      distances.merge(child.getId(), distance, Math::min);
      collectDescendants(child.getId(), distance + 1, childrenByParent, distances);
    }
  }

  /** {@code in ()} vazio não é válido em JPQL; o sentinela nunca casa com id real. */
  List<Long> sentinelIfEmpty(List<Long> ancestorIds) {
    return ancestorIds.isEmpty() ? List.of(-1L) : ancestorIds;
  }

  /**
   * Pagina preservando a ordem do ranking.
   *
   * <p>O banco não sabe ordenar por especificidade, então o recorte da página acontece sobre a
   * lista já ordenada e os motoristas são reordenados depois da busca — ordenar só o que o
   * repositório devolvesse embaralharia o ranking a cada página.
   */
  Page<DriverSearchResponseDTO> pageInRankOrder(List<Long> ranked, Pageable pageable) {
    int from = (int) Math.min(pageable.getOffset(), ranked.size());
    int to = Math.min(from + pageable.getPageSize(), ranked.size());
    List<Long> pageIds = ranked.subList(from, to);
    if (pageIds.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, ranked.size());
    }

    Map<Long, DriverModel> byId = new HashMap<>();
    drivers.findActiveByIds(pageIds, Pageable.unpaged()).forEach(d -> byId.put(d.getId(), d));

    Map<Long, List<String>> areaNamesByDriver = findAreaNames(pageIds);

    List<DriverSearchResponseDTO> content =
        pageIds.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(driver -> toResponse(driver, areaNamesByDriver))
            .toList();
    return new PageImpl<>(content, pageable, ranked.size());
  }

  /**
   * Nomes das regiões por motorista, em uma consulta só para a página inteira. Buscar por motorista
   * dentro do laço seria uma query por linha (regra 17).
   */
  Map<Long, List<String>> findAreaNames(List<Long> driverIds) {
    Map<Long, List<String>> namesByDriver = new HashMap<>();
    for (DriverServiceAreaModel area : areas.findByDriverIds(driverIds)) {
      String name =
          area.getDistrict() == null ? area.getCity().getName() : area.getDistrict().getName();
      namesByDriver.computeIfAbsent(area.getDriver().getId(), id -> new ArrayList<>()).add(name);
    }
    return namesByDriver;
  }

  DriverSearchResponseDTO toResponse(
      DriverModel driver, Map<Long, List<String>> areaNamesByDriver) {
    return new DriverSearchResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getPhoto(),
        driver.getRating(),
        driver.getBasePrice(),
        driver.getExperienceYears(),
        driver.isAvailable(),
        areaNamesByDriver.getOrDefault(driver.getId(), List.of()));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
