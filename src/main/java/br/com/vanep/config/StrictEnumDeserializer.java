package br.com.vanep.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class StrictEnumDeserializer extends ValueDeserializer<Object> {

  private final Class<?> enumType;
  private final ValueDeserializer<Object> delegate;

  @SuppressWarnings("unchecked")
  StrictEnumDeserializer(Class<?> enumType, ValueDeserializer<?> delegate) {
    this.enumType = enumType;
    this.delegate = (ValueDeserializer<Object>) delegate;
  }

  @Override
  public Object deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.hasToken(JsonToken.VALUE_STRING)) {
      String value = parser.getString();
      if (!value.equals(value.trim())) {
        return context.handleWeirdStringValue(
            enumType, value, "leading or trailing whitespace is not allowed");
      }
    }

    return delegate.deserialize(parser, context);
  }

  @Override
  public ValueDeserializer<?> createContextual(
      DeserializationContext context, BeanProperty property) {
    ValueDeserializer<?> contextualDelegate = delegate.createContextual(context, property);
    if (contextualDelegate == delegate) {
      return this;
    }
    return new StrictEnumDeserializer(enumType, contextualDelegate);
  }

  @Override
  public ValueDeserializer<?> getDelegatee() {
    return delegate;
  }

  @Override
  public Class<?> handledType() {
    return enumType;
  }

  @Override
  public boolean isCachable() {
    return delegate.isCachable();
  }
}
