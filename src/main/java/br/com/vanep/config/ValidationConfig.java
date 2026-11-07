package br.com.vanep.config;

import br.com.vanep.trip.service.TripService;
import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @FutureOrPresent} and friends compare with the validator's clock, which defaults to the
 * JVM zone. The container runs in UTC, so between 21h and midnight in Brasília "today" would
 * already be in the past.
 */
@Configuration
public class ValidationConfig {

  @Bean
  public ValidationConfigurationCustomizer serviceZoneClockProvider() {
    Clock serviceClock = Clock.system(TripService.SERVICE_ZONE);
    return configuration -> configuration.clockProvider(() -> serviceClock);
  }
}
