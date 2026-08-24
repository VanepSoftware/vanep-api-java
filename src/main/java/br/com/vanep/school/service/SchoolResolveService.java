package br.com.vanep.school.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.school.dto.SchoolResolveRequestDTO;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolve um place de escola em uma linha de {@code school}, criando na primeira vez.
 *
 * <p>É escrita, e por isso {@code POST}. Um {@code GET} que cria linha é pré-buscável por
 * intermediários — prefetch de browser criaria escola (D9).
 */
@Service
public class SchoolResolveService {

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

  /** Resultado do resolve: a escola e se ela nasceu agora — o controller traduz em 201 ou 200. */
  public record Resolution(SchoolModel school, boolean created) {}

  @Transactional
  public Resolution resolve(String callerUid, SchoolResolveRequestDTO request) {
    // O limite vem antes da chamada ao Google de propósito (R6): cada placeId
    // distinto custa um Place Details pago E cria uma linha. Varrer ids seria
    // gastar dinheiro nosso e sujar uma tabela compartilhada.
    if (!rateLimiter.tryAcquire("school-resolve:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    PlaceDetailsResponseDTO details =
        places.findPlaceDetailsWithName(request.placeId(), request.sessionToken());

    return schools
        .findByGooglePlaceId(details.id())
        .map(existing -> new Resolution(existing, false))
        .orElseGet(() -> new Resolution(create(details), true));
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

  /**
   * {@code displayName} é o nome real da escola ("Colégio Objetivo"). O {@code formattedAddress} é
   * a rede de segurança para o caso de o mask não trazer o nome — melhor um rótulo feio que uma
   * violação de NOT NULL.
   */
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
