package br.com.vanep.config;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.json.JsonMapper;

// Hibernate 7.2 only ships a Jackson 2 mapper, which writes dates as arrays; this one reads and
// writes JSON columns with the same JsonMapper as the API.
public class HibernateJsonFormatMapper extends AbstractJsonFormatMapper {

  private final JsonMapper jsonMapper;

  public HibernateJsonFormatMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public <T> T fromString(CharSequence json, Type type) {
    return jsonMapper.readValue(json.toString(), jsonMapper.constructType(type));
  }

  @Override
  public <T> String toString(T value, Type type) {
    return jsonMapper.writerFor(jsonMapper.constructType(type)).writeValueAsString(value);
  }
}
