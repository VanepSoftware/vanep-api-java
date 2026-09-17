package br.com.vanep.config;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

@Component
public class JsonNullableValueExtractor implements ValueExtractor<JsonNullable<@ExtractedValue ?>> {

  @Override
  public void extractValues(JsonNullable<?> originalValue, ValueReceiver receiver) {
    if (originalValue != null && originalValue.isPresent()) {
      receiver.value(null, originalValue.get());
    }
  }
}
