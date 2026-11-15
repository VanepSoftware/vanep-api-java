package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.trip.service.TripService;
import jakarta.validation.Configuration;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class ValidationConfigTest {

  @Test
  void theValidatorClockRunsInTheServiceZone() {
    Configuration<?> configuration = Validation.byDefaultProvider().configure();
    new ValidationConfig().serviceZoneClockProvider().customize(configuration);

    try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
      assertThat(factory.getClockProvider().getClock().getZone())
          .isEqualTo(TripService.SERVICE_ZONE);
    }
  }
}
