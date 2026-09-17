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
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class PlacesClient {
  private static final Logger log = LoggerFactory.getLogger(PlacesClient.class);

  static final String FIELD_MASK = "id,formattedAddress,addressComponents,types";

  static final String FIELD_MASK_ID_ONLY = "id";

  static final String FIELD_MASK_WITH_NAME = FIELD_MASK + ",displayName";

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

  public PlaceDetailsResponseDTO findPlaceDetails(String placeId, String sessionToken) {
    return findPlaceDetails(placeId, sessionToken, FIELD_MASK);
  }

  public PlaceDetailsResponseDTO findPlaceDetails(String placeId) {
    return findPlaceDetails(placeId, null, FIELD_MASK);
  }

  public PlaceDetailsResponseDTO findPlaceDetailsWithName(String placeId, String sessionToken) {
    return findPlaceDetails(placeId, sessionToken, FIELD_MASK_WITH_NAME);
  }

  public void closeAutocompleteSession(String placeId, String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) {
      return;
    }
    findPlaceDetails(placeId, sessionToken, FIELD_MASK_ID_ONLY);
  }

  private PlaceDetailsResponseDTO findPlaceDetails(
      String placeId, String sessionToken, String fieldMask) {
    if (placeId == null || placeId.isBlank()) {
      throw new IllegalArgumentException("placeId não pode ser nulo ou vazio.");
    }

    String cacheKey = fieldMask + "|" + placeId;
    if (hasSession(sessionToken)) {
      PlaceDetailsResponseDTO fresh = fetchFromGoogle(placeId, sessionToken, fieldMask);
      cache.put(cacheKey, fresh);
      return fresh;
    }
    PlaceDetailsResponseDTO cached = cache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }
    PlaceDetailsResponseDTO fetched = fetchFromGoogle(placeId, null, fieldMask);
    cache.put(cacheKey, fetched);
    return fetched;
  }

  boolean hasSession(String sessionToken) {
    return sessionToken != null && !sessionToken.isBlank();
  }

  private PlaceDetailsResponseDTO fetchFromGoogle(
      String placeId, String sessionToken, String fieldMask) {
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
              .header("X-Goog-FieldMask", fieldMask)
              .retrieve()
              .onStatus(
                  status -> status.value() == 400 || status.value() == 404,
                  (request, clientResponse) -> {
                    throw new PlaceNotFoundException(placeId);
                  })
              .onStatus(
                  status -> status.isError(),
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
