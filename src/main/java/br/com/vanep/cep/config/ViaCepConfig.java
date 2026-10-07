package br.com.vanep.cep.config;

import br.com.vanep.auth.security.RateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ViaCepConfig {
  @Bean
  public RestClient viaCepRestClient(
      @Value("${vanep.viacep.base-url}") String baseUrl,
      @Value("${vanep.viacep.connect-timeout-seconds}") long connectTimeoutSeconds,
      @Value("${vanep.viacep.read-timeout-seconds}") long readTimeoutSeconds) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  @Bean("viaCepRateLimiter")
  public RateLimiter viaCepRateLimiter(
      @Value("${vanep.viacep.rate-limit.enabled:true}") boolean enabled,
      @Value("${vanep.viacep.rate-limit.capacity:20}") int capacity,
      @Value("${vanep.viacep.rate-limit.window-seconds:60}") long windowSeconds) {
    return new RateLimiter(enabled, capacity, windowSeconds);
  }
}
