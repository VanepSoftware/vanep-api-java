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

  /**
   * Mask do caminho de escola. Acrescenta {@code displayName}, que é <b>SKU Pro</b> — cobrado por
   * cima do Essentials na mesma chamada (Q6).
   *
   * <p>Vale a troca porque uma escola nasce uma vez por {@code placeId} distinto, não por
   * requisição: o custo Pro é limitado pelo número de escolas que existem, não pelo tráfego. A
   * alternativa (o cliente enviar o nome que já recebeu de graça do autocomplete) sairia mais
   * barata, mas o nome da escola é rótulo de um recurso <b>compartilhado</b> — quem cria vê o nome
   * aparecer para todo mundo, e um texto plantado envenenaria a listagem alheia.
   */
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
    return findPlaceDetails(placeId, sessionToken, FIELD_MASK);
  }

  public PlaceDetailsResponseDTO findPlaceDetails(String placeId) {
    return findPlaceDetails(placeId, null, FIELD_MASK);
  }

  /** Caminho de escola: traz também o nome do lugar. Custa um SKU Pro a mais — ver Q6. */
  public PlaceDetailsResponseDTO findPlaceDetailsWithName(String placeId, String sessionToken) {
    return findPlaceDetails(placeId, sessionToken, FIELD_MASK_WITH_NAME);
  }

  private PlaceDetailsResponseDTO findPlaceDetails(
      String placeId, String sessionToken, String fieldMask) {
    if (placeId == null || placeId.isBlank()) {
      throw new IllegalArgumentException("placeId não pode ser nulo ou vazio.");
    }
    // A chave inclui o mask: uma entrada gravada com o mask estreito não tem
    // displayName, e servi-la ao caminho de escola devolveria nome nulo.
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
