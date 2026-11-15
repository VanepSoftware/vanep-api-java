package br.com.vanep.config;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;

final class StrictEnumDeserializerModifier extends ValueDeserializerModifier {

  @Override
  public ValueDeserializer<?> modifyEnumDeserializer(
      DeserializationConfig config,
      JavaType type,
      BeanDescription.Supplier beanDescriptionSupplier,
      ValueDeserializer<?> deserializer) {
    return new StrictEnumDeserializer(type.getRawClass(), deserializer);
  }
}
