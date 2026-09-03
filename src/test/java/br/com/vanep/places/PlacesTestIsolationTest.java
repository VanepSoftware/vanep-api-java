package br.com.vanep.places;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards constitution rule 50: no test calls a real external API.
 *
 * <p>The rule is enforced by configuration, not discipline — but configuration can be changed.
 * This test fails if someone points the test profile at a real host, which is how a suite starts
 * burning paid quota on every push without anyone noticing.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlacesTestIsolationTest {

  @Value("${vanep.google.places.base-url}")
  private String baseUrl;

  @Value("${vanep.google.places.api-key}")
  private String apiKey;

  @Test
  void testProfileNeverPointsAtARealPlacesHost() {
    String host = URI.create(baseUrl).getHost();

    assertThat(host)
        .as("test profile base-url must be unreachable (rule 50)")
        .isIn("localhost", "127.0.0.1", "::1");
    assertThat(baseUrl).doesNotContain("googleapis.com");
  }

  @Test
  void testProfileNeverCarriesARealApiKey() {
    assertThat(apiKey)
        .as("test profile api key must be obviously fake (rule 50)")
        .contains("test");
  }
}
