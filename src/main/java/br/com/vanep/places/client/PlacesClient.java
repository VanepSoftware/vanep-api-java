package br.com.vanep.places.client;

import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.places.exception.PlaceLookupException;
import br.com.vanep.places.exception.PlaceNotFoundException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Acesso ao {@code Place Details} do Google Places (New). É o único ponto que fala com o Google.
 */
@Component
public class PlacesClient {

  private static final Logger log = LoggerFactory.getLogger(PlacesClient.class);

  /**
   * O field mask decide o SKU cobrado (Q2). Estes três campos ficam todos em <i>Place Details
   * Essentials</i>, uma cobrança só. Não acrescente campo aqui sem conferir em qual SKU ele cai.
   */
  static final String FIELD_MASK = "id,formattedAddress,addressComponents";

  private final RestClient restClient;
  private final Cache<String, PlaceDetailsResponseDTO> cache;

  public PlacesClient(
      RestClient placesRestClient,
      @Value("${vanep.google.places.cache-ttl-minutes}") long cacheTtlMinutes,
      @Value("${vanep.google.places.cache-max-size}") long cacheMaxSize) {
    this.restClient = placesRestClient;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
            .maximumSize(cacheMaxSize)
            .build();
  }

  /**
   * Resolve um {@code placeId}, respeitando a regra de sessão do D5.
   *
   * <p>Com {@code sessionToken} presente a chamada ao Google é <b>sempre</b> feita, mesmo havendo
   * entrada em cache: é ela que encerra a sessão de autocomplete e faz aquelas teclas entrarem no
   * SKU gratuito de sessão. Servir do cache aqui economizaria um {@code Place Details} e faria o
   * cliente pagar cada tecla avulsa — sairia mais caro que não cachear.
   *
   * <p>Sem token, o {@code placeId} veio de algo já persistido e o cache vale.
   */
  public PlaceDetailsResponseDTO findPlaceDetails(String placeId, String sessionToken) {
    if (placeId == null || placeId.isBlank()) {
      throw new IllegalArgumentException("placeId não pode ser nulo ou vazio.");
    }
    if (hasSession(sessionToken)) {
      PlaceDetailsResponseDTO fresh = fetchFromGoogle(placeId, sessionToken);
      cache.put(placeId, fresh);
      return fresh;
    }
    PlaceDetailsResponseDTO cached = cache.getIfPresent(placeId);
    if (cached != null) {
      return cached;
    }
    PlaceDetailsResponseDTO fetched = fetchFromGoogle(placeId, null);
    cache.put(placeId, fetched);
    return fetched;
  }

  public PlaceDetailsResponseDTO findPlaceDetails(String placeId) {
    return findPlaceDetails(placeId, null);
  }

  boolean hasSession(String sessionToken) {
    return sessionToken != null && !sessionToken.isBlank();
  }

  private PlaceDetailsResponseDTO fetchFromGoogle(String placeId, String sessionToken) {
    try {
      PlaceDetailsResponseDTO response =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    uriBuilder.path("/v1/places/{placeId}");
                    if (hasSession(sessionToken)) {
                      uriBuilder.queryParam("sessionToken", sessionToken);
                    }
                    return uriBuilder.build(placeId);
                  })
              .header("X-Goog-FieldMask", FIELD_MASK)
              .retrieve()
              // 400 e 404 dizem que o placeId enviado não presta — é dado de entrada.
              // Qualquer outro erro (401, 403, 429, 5xx) é credencial, quota ou o
              // fornecedor fora do ar: problema nosso, e a distinção decide se o
              // chamador devolve 4xx ou 5xx.
              .onStatus(
                  status -> status.value() == 400 || status.value() == 404,
                  (request, clientResponse) -> {
                    throw new PlaceNotFoundException(placeId);
                  })
              .onStatus(
                  HttpStatusCode::isError,
                  (request, clientResponse) -> {
                    throw new PlaceLookupException(
                        "Google Places respondeu " + clientResponse.getStatusCode() + ".");
                  })
              .toEntity(PlaceDetailsResponseDTO.class)
              .getBody();

      if (response == null || response.id() == null) {
        throw new PlaceNotFoundException(placeId);
      }
      return response;
    } catch (PlaceNotFoundException | PlaceLookupException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      log.warn("Places request failed for placeId={}: {}", placeId, ex.getMessage());
      throw new PlaceLookupException("Falha de comunicação com o Google Places.", ex);
    } catch (RuntimeException ex) {
      log.warn("Unexpected Places failure for placeId={}", placeId, ex);
      throw new PlaceLookupException("Resposta inesperada do Google Places.", ex);
    }
  }
}
