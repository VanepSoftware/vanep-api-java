package br.com.vanep.school.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.dto.SchoolResolveRequestDTO;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolResolveService {
  private static final Set<String> SCHOOL_TYPES = Set.of("school", "educational_institution");

  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final SchoolRepository schools;
  private final RateLimiter rateLimiter;
  private final MessageSource messages;

  public SchoolResolveService(
      PlacesClient places,
      LocationResolverService resolver,
      SchoolRepository schools,
      @Qualifier("placesRateLimiter") RateLimiter rateLimiter,
      MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.schools = schools;
    this.rateLimiter = rateLimiter;
    this.messages = messages;
  }

  public record Resolution(SchoolModel school, boolean created) {}

  @Transactional
  public Resolution resolve(String callerUid, SchoolResolveRequestDTO request) {
    Optional<SchoolModel> known = schools.findByGooglePlaceId(request.placeId());
    if (known.isPresent()) {
      places.closeAutocompleteSession(request.placeId(), request.sessionToken());
      return new Resolution(known.get(), false);
    }

    if (!rateLimiter.tryAcquire("school-resolve:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    PlaceDetailsResponseDTO details =
        places.findPlaceDetailsWithName(request.placeId(), request.sessionToken());
    requireSchool(details);

    return schools
        .findByGooglePlaceId(details.id())
        .map(existing -> new Resolution(existing, false))
        .orElseGet(() -> new Resolution(create(details), true));
  }

  private void requireSchool(PlaceDetailsResponseDTO details) {
    if (details.types().stream().noneMatch(SCHOOL_TYPES::contains)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("school.place.not_a_school"));
    }
  }

  private SchoolModel create(PlaceDetailsResponseDTO details) {
    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(details);

    SchoolModel school = new SchoolModel();
    school.setGooglePlaceId(details.id());
    school.setName(resolveName(details));
    school.setCity(chain.city());
    school.setDistrict(chain.deepestDistrict().orElse(null));
    return schools.save(school);
  }

  String resolveName(PlaceDetailsResponseDTO details) {
    String displayName = details.displayNameText();
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    return details.formattedAddress();
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
