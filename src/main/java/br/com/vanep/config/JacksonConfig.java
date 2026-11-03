package br.com.vanep.config;

import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

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

  @Bean
  public JsonMapperBuilderCustomizer strictJsonTypesCustomizer() {
    return builder ->
        builder
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .withCoercionConfig(
                LogicalType.Textual,
                coercionConfig -> {
                  coercionConfig.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                  coercionConfig.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                  coercionConfig.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                });
  }

  @Bean
  public JacksonModule strictEnumValuesModule() {
    return new SimpleModule("strict-enum-values")
        .setDeserializerModifier(new StrictEnumDeserializerModifier());
  }
}
