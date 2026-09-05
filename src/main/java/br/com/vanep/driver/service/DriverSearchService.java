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
    if (!rateLimiter.tryAcquire("driver-search:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    Optional<ResolvedLocationChainDTO> anchor =
        resolver.resolveAnchor(places.findPlaceDetails(placeId, sessionToken));

    if (anchor.isEmpty()) {
      return Page.empty(pageable);
    }

    List<Long> ranked = rankDriverIds(anchor.get());
    if (ranked.isEmpty()) {
      return Page.empty(pageable);
    }
    return pageInRankOrder(ranked, pageable);
  }

  List<Long> rankDriverIds(ResolvedLocationChainDTO anchor) {
    Map<Long, Integer> distanceByDistrict = distanceByDistrict(anchor);
    int wholeCityRank = Integer.MAX_VALUE;

    boolean anchoredOnTheWholeCity =
        anchor.deepestDistrict().isEmpty() && !anchor.anchoredAboveTheDistrictComponents();

    List<Object[]> matches =
        anchoredOnTheWholeCity
            ? areas.findDriverMatchesInCity(anchor.city().getId())
            : areas.findDriverMatchesCoveringPoint(
                anchor.city().getId(), sentinelIfEmpty(List.copyOf(distanceByDistrict.keySet())));

    Map<Long, Integer> bestRankByDriver = new HashMap<>();
    for (Object[] match : matches) {
      Long driverId = (Long) match[0];
      Long districtId = (Long) match[1];
      Integer distance =
          rankOf(districtId, distanceByDistrict, anchoredOnTheWholeCity, wholeCityRank);
      if (distance == null) {
        continue;
      }
      bestRankByDriver.merge(driverId, distance, (a, b) -> Math.min(a, b));
    }

    return bestRankByDriver.entrySet().stream()
        .sorted(
            Map.Entry.<Long, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
        .map(entry -> entry.getKey())
        .toList();
  }

  Integer rankOf(
      Long districtId,
      Map<Long, Integer> distanceByDistrict,
      boolean anchoredOnTheWholeCity,
      int wholeCityRank) {
    if (districtId == null) {
      return wholeCityRank;
    }
    return anchoredOnTheWholeCity ? 0 : distanceByDistrict.get(districtId);
  }

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
      distances.merge(child.getId(), distance, (a, b) -> Math.min(a, b));
      collectDescendants(child.getId(), distance + 1, childrenByParent, distances);
    }
  }

  List<Long> sentinelIfEmpty(List<Long> ancestorIds) {
    return ancestorIds.isEmpty() ? List.of(-1L) : ancestorIds;
  }

  Page<DriverSearchResponseDTO> pageInRankOrder(List<Long> ranked, Pageable pageable) {
    int from = (int) Math.min(pageable.getOffset(), ranked.size());
    int to = Math.min(from + pageable.getPageSize(), ranked.size());
    List<Long> pageIds = ranked.subList(from, to);
    if (pageIds.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, ranked.size());
    }

    Map<Long, DriverModel> byId = new HashMap<>();
    drivers.findSearchableByIds(pageIds, Pageable.unpaged()).forEach(d -> byId.put(d.getId(), d));

    Map<Long, List<String>> areaNamesByDriver = findAreaNames(pageIds);

    List<DriverSearchResponseDTO> content =
        pageIds.stream()
            .map(byId::get)
            .filter(Objects::nonNull)
            .map(driver -> toResponse(driver, areaNamesByDriver))
            .toList();
    return new PageImpl<>(content, pageable, ranked.size());
  }

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
