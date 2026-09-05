package br.com.vanep.places;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
    assertThat(apiKey).as("test profile api key must be obviously fake (rule 50)").contains("test");
  }
}
