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

/**
 * Resolve um place de escola em uma linha de {@code school}, criando na primeira vez.
 *
 * <p>É escrita, e por isso {@code POST}. Um {@code GET} que cria linha é pré-buscável por
 * intermediários — prefetch de browser criaria escola (D9).
 */
@Service
public class SchoolResolveService {

  /** Os dois {@code types} que o Google dá a uma escola — ver as fixtures da fase 1. */
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

  /** Resultado do resolve: a escola e se ela nasceu agora — o controller traduz em 201 ou 200. */
  public record Resolution(SchoolModel school, boolean created) {}

  /**
   * Ordem: banco primeiro, Google depois.
   *
   * <p>A escola que já existe é o caso comum — o pai reabrindo o mesmo colégio —, e para ele nada
   * do Google é necessário. Perguntar antes de olhar a tabela pagava um SKU Pro para descobrir algo
   * que já estava salvo, e ainda consumia o limite do R6, o que fazia o pai levar 429 por reabrir a
   * mesma escola. Se o app abriu uma sessão de autocomplete, ela é encerrada pelo SKU gratuito.
   */
  @Transactional
  public Resolution resolve(String callerUid, SchoolResolveRequestDTO request) {
    Optional<SchoolModel> known = schools.findByGooglePlaceId(request.placeId());
    if (known.isPresent()) {
      places.closeAutocompleteSession(request.placeId(), request.sessionToken());
      return new Resolution(known.get(), false);
    }

    // O limite guarda o caminho caro (R6): cada placeId desconhecido custa um
    // Place Details pago E cria uma linha numa tabela compartilhada. Varrer ids
    // seria gastar dinheiro nosso e sujar o catálogo de todo mundo.
    if (!rateLimiter.tryAcquire("school-resolve:" + callerUid)) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, message("location.place.rate_limited"));
    }

    PlaceDetailsResponseDTO details =
        places.findPlaceDetailsWithName(request.placeId(), request.sessionToken());
    requireSchool(details);

    // O Google pode devolver um id canônico diferente do pedido quando o place
    // foi sucedido por outro. Só aqui dá para saber, então a tabela é conferida
    // de novo antes de criar.
    return schools
        .findByGooglePlaceId(details.id())
        .map(existing -> new Resolution(existing, false))
        .orElseGet(() -> new Resolution(create(details), true));
  }

  /**
   * Um {@code placeId} qualquer não vira escola.
   *
   * <p>A Q6 trancou o <b>nome</b> para ninguém plantar texto na listagem compartilhada, e o id
   * ficou aberto: shopping, farmácia ou a casa de alguém entravam no catálogo que todo mundo vê,
   * com o nome oficial do Google e cara de escola de verdade. O mesmo estrago, por outra porta.
   */
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
