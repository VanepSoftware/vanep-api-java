package br.com.vanep.cep.client;

import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.cep.exception.ViaCepLookupException;
import br.com.vanep.cep.exception.ViaCepNotFoundException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ViaCepClient {
  private final RestClient restClient;
  private final Cache<String, ViaCepResponseDTO> cache;

  public ViaCepClient(
      @Qualifier("viaCepRestClient") RestClient viaCepRestClient,
      @Value("${vanep.viacep.cache-ttl-minutes}") long cacheTtlMinutes,
      @Value("${vanep.viacep.cache-max-size}") long cacheMaxSize) {
    this.restClient = viaCepRestClient;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
            .maximumSize(cacheMaxSize)
            .build();
  }

  public ViaCepResponseDTO findByCep(String cep) {
    ViaCepResponseDTO cached = cache.getIfPresent(cep);
    if (cached != null) {
      return cached;
    }
    ViaCepResponseDTO fetched = fetchFromViaCep(cep);
    cache.put(cep, fetched);
    return fetched;
  }

  private ViaCepResponseDTO fetchFromViaCep(String cep) {
    try {
      ViaCepResponseDTO response =
          restClient
              .get()
              .uri("/ws/{cep}/json/", cep)
              .retrieve()
              .onStatus(
                  status -> status.isError(),
                  (request, clientResponse) -> {
                    throw new ViaCepLookupException(
                        "ViaCEP responded " + clientResponse.getStatusCode() + ".");
                  })
              .toEntity(ViaCepResponseDTO.class)
              .getBody();

      if (response == null || response.isError()) {
        throw new ViaCepNotFoundException(cep);
      }
      return response;
    } catch (ViaCepNotFoundException | ViaCepLookupException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      throw new ViaCepLookupException("Failed to reach ViaCEP.", ex);
    } catch (RuntimeException ex) {
      throw new ViaCepLookupException("Unexpected ViaCEP response.", ex);
    }
  }
}
