package br.com.vanep.auth.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.node.MissingNode;

/**
 * Jackson 3 mapping for the form-login principal stored in {@code oauth2_authorization}. Spring
 * Security's allowlist knows {@code User}, not this subclass, and {@code User}'s mixin does not
 * apply to it.
 */
public final class VanepUserDetailsDeserializer extends ValueDeserializer<VanepUserDetails> {

  private static final TypeReference<Set<GrantedAuthority>> GRANTED_AUTHORITY_SET =
      new TypeReference<>() {};

  @Override
  public VanepUserDetails deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    JsonNode node = context.readTree(parser);
    JsonNode authoritiesNode = read(node, "authorities");
    Set<GrantedAuthority> authorities =
        context.readTreeAsValue(
            authoritiesNode, context.getTypeFactory().constructType(GRANTED_AUTHORITY_SET));
    JsonNode passwordNode = read(node, "password");
    String password = passwordNode.isMissingNode() ? null : passwordNode.asString(null);
    VanepUserDetails user =
        new VanepUserDetails(
            read(node, "username").asString(),
            password,
            text(node, "userToken"),
            read(node, "enabled").asBoolean(),
            read(node, "accountNonExpired").asBoolean(),
            read(node, "credentialsNonExpired").asBoolean(),
            read(node, "accountNonLocked").asBoolean(),
            authorities);
    if (password == null) {
      user.eraseCredentials();
    }
    return user;
  }

  private static JsonNode read(JsonNode node, String field) {
    return node.has(field) ? node.get(field) : MissingNode.getInstance();
  }

  private static @Nullable String text(JsonNode node, String field) {
    JsonNode value = read(node, field);
    return value.isMissingNode() || value.isNull() ? null : value.asString();
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  @JsonDeserialize(using = VanepUserDetailsDeserializer.class)
  @JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      isGetterVisibility = JsonAutoDetect.Visibility.NONE)
  public abstract static class Mixin {}
}
