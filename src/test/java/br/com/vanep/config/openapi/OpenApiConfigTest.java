package br.com.vanep.config.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void vanepOpenApi_hasExpectedMetadataAndSecurityScheme() {
    OpenAPI api = new OpenApiConfig().vanepOpenApi();

    assertThat(api.getInfo().getTitle()).isEqualTo("Vanep API");
    assertThat(api.getInfo().getVersion()).isEqualTo("0.0.1-SNAPSHOT");
    assertThat(api.getComponents().getSecuritySchemes()).containsKey("httpBasic");
    assertThat(api.getSecurity()).isNotEmpty();
  }
}
