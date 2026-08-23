package br.com.vanep.places.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Monta o {@link RestClient} do Google Places.
 *
 * <p>Separado do {@code PlacesClient} de propósito: assim o client recebe um {@link RestClient}
 * pronto e os testes podem injetar um ligado ao {@code MockRestServiceServer}, sem que a fábrica de
 * requisições configurada aqui sobrescreva o stub (regra 50 — nenhum teste chama a API real).
 */
@Configuration
public class PlacesRestClientConfig {

  @Bean
  public RestClient placesRestClient(
      @Value("${vanep.google.places.base-url}") String baseUrl,
      @Value("${vanep.google.places.api-key}") String apiKey,
      @Value("${vanep.google.places.connect-timeout-seconds}") long connectTimeoutSeconds,
      @Value("${vanep.google.places.read-timeout-seconds}") long readTimeoutSeconds) {

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .defaultHeader("X-Goog-Api-Key", apiKey)
        .build();
  }
}
