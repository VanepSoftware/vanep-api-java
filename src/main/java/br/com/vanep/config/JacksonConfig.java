package br.com.vanep.config;

import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;

@Configuration
public class JacksonConfig {

  /**
   * Registers JsonNullable support for Jackson 3 (Spring Boot 4). Beans of type {@link
   * JacksonModule} are auto-applied by Spring Boot's JsonMapper builder.
   */
  @Bean
  public JacksonModule jsonNullableModule() {
    return new JsonNullableJackson3Module();
  }
}
