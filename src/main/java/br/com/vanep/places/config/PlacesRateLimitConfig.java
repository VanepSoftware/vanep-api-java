package br.com.vanep.places.config;

import br.com.vanep.auth.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlacesRateLimitConfig {
  @Bean("placesRateLimiter")
  public RateLimiter placesRateLimiter(
      @Value("${vanep.google.places.rate-limit.enabled:true}") boolean enabled,
      @Value("${vanep.google.places.rate-limit.capacity:20}") int capacity,
      @Value("${vanep.google.places.rate-limit.window-seconds:60}") long windowSeconds) {
    return new RateLimiter(enabled, capacity, windowSeconds);
  }
}
