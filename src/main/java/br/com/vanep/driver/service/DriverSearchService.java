package br.com.vanep.driver.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.dto.DriverSearchResponseDTO;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverservicearea.repository.DriverServiceAreaRepository;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Busca motoristas que cobrem <b>origem e destino</b> ao mesmo tempo.
 *
 * <p>Read-only por construção (D3): usa {@code resolveAnchor}, que nunca escreve. Se a busca
 * criasse nós, a árvore cresceria com termos de busca e o índice do R2 perderia sentido — e um
 * {@code GET} que escreve seria pré-buscável por intermediários.
 */
@Service
public class DriverSearchService {

  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final DriverServiceAreaRepository areas;
  private final DriverRepository drivers;
  private final RateLimiter rateLimiter;
  private final MessageSource messages;

  public DriverSearchService(
      PlacesClient places,
      LocationResolverService resolver,
      DriverServiceAreaRepository areas,
      DriverRepository drivers,
      @Qualifier("placesRateLimiter") RateLimiter rateLimiter,
      MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.areas = areas;
    this.drivers = drivers;
    this.rateLimiter = rateLimiter;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public Page<DriverSearchResponseDTO> search(
      String callerUid,
      String originPlaceId,
      String originSessionToken,
      String destinationPlaceId,
      String destinationSessionToken,
      Pageable pageable) {

    // Antes de qualquer chamada paga: cada busca dispara DOIS Place Details a
    // partir de ids que o cliente escolhe (R6).
    if (!rateLimiter.tryAcquire("driver-search:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    Optional<ResolvedLocationChainDTO> origin =
        resolver.resolveAnchor(places.findPlaceDetails(originPlaceId, originSessionToken));
    Optional<ResolvedLocationChainDTO> destination =
        resolver.resolveAnchor(
            places.findPlaceDetails(destinationPlaceId, destinationSessionToken));

    // Âncora vazia significa que nem a cidade existe na árvore. Nenhum motorista
    // pode ter cadastrado ali, então o resultado é vazio — não é erro.
    if (origin.isEmpty() || destination.isEmpty()) {
      return Page.empty(pageable);
    }

    Set<Long> covering = new LinkedHashSet<>(findDriverIdsCovering(origin.get()));
    covering.retainAll(Set.copyOf(findDriverIdsCovering(destination.get())));

    if (covering.isEmpty()) {
      return Page.empty(pageable);
    }
    return drivers.findActiveByIds(covering, pageable).map(this::toResponse);
  }

  /**
   * Uma âncora sem distrito tem <b>dois</b> significados, e confundi-los devolve motorista errado.
   *
   * <ul>
   *   <li>O place não trouxe componente de distrito — o usuário buscou a cidade mesmo. Todo
   *       motorista da cidade serve.
   *   <li>O place trouxe distrito, mas a árvore ainda não o conhece. O ponto é específico e ninguém
   *       cadastrou aquela região: só quem declarou a <b>cidade inteira</b> cobre. Tratar este caso
   *       como busca por cidade faria um motorista de Taguatinga aparecer numa busca por Águas
   *       Claras.
   * </ul>
   */
  List<Long> findDriverIdsCovering(ResolvedLocationChainDTO anchor) {
    Optional<DistrictModel> deepest = anchor.deepestDistrict();
    if (deepest.isEmpty()) {
      if (anchor.anchoredAboveTheDistrictComponents()) {
        // Ponto específico em região desconhecida: só cobertura de cidade inteira.
        // O sentinela existe porque `in ()` vazio não é válido em JPQL.
        return areas.findDriverIdsCoveringPoint(anchor.city().getId(), List.of(-1L));
      }
      return areas.findDriverIdsInCity(anchor.city().getId());
    }
    List<Long> ancestorIds = new ArrayList<>();
    resolver.findAncestors(deepest.get()).forEach(district -> ancestorIds.add(district.getId()));
    return areas.findDriverIdsCoveringPoint(anchor.city().getId(), ancestorIds);
  }

  DriverSearchResponseDTO toResponse(DriverModel driver) {
    return new DriverSearchResponseDTO(
        driver.getToken(),
        driver.getUser().getName(),
        driver.getPhoto(),
        driver.getRating(),
        driver.getBasePrice(),
        driver.getExperienceYears(),
        driver.isAvailable());
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
