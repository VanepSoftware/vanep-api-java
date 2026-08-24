package br.com.vanep.places.config;

import br.com.vanep.auth.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Limite dedicado aos endpoints que gastam dinheiro por requisição (R6).
 *
 * <p>Reusa a classe {@link RateLimiter} em vez de duplicar a lógica de janela (regra 6), mas com
 * budget próprio: o limite global protege contra abuso de login e é medido por IP; este protege a
 * fatura do Google e é medido por usuário autenticado. Compartilhar o mesmo balde faria uma busca
 * legítima ser bloqueada por causa de tráfego não relacionado.
 */
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
