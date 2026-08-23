package br.com.vanep.places;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guarda a regra 50 da constituição: nenhum teste chama API externa real.
 *
 * <p>A regra é aplicada por configuração, não por disciplina — mas configuração pode ser alterada.
 * Este teste falha se alguém apontar o profile de teste para um host de verdade, que é como uma
 * suíte passa a gastar quota paga a cada push sem ninguém perceber.
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
        .as("base-url do profile de teste tem de ser inalcançável (regra 50)")
        .isIn("localhost", "127.0.0.1", "::1");
    assertThat(baseUrl).doesNotContain("googleapis.com");
  }

  @Test
  void testProfileNeverCarriesARealApiKey() {
    assertThat(apiKey)
        .as("chave do profile de teste tem de ser obviamente falsa (regra 50)")
        .contains("test");
  }
}
