package br.com.vanep.auth.security;

import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/** JSON mapper for authorizations stored by {@link JdbcOAuth2AuthorizationService}. */
public final class OAuth2AuthorizationJackson {

  private OAuth2AuthorizationJackson() {}

  public static JsonMapper jsonMapper() {
    ClassLoader loader = JdbcOAuth2AuthorizationService.class.getClassLoader();
    BasicPolymorphicTypeValidator.Builder subtypes =
        BasicPolymorphicTypeValidator.builder().allowIfSubType(VanepUserDetails.class);
    return JsonMapper.builder()
        .addModules(SecurityJacksonModules.getModules(loader, subtypes))
        .addMixIn(VanepUserDetails.class, VanepUserDetailsDeserializer.Mixin.class)
        .build();
  }

  public static void install(
      JdbcOAuth2AuthorizationService service, RegisteredClientRepository clients) {
    JsonMapper mapper = jsonMapper();
    JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper rowMapper =
        new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, mapper);
    rowMapper.setLobHandler(new DefaultLobHandler());
    service.setAuthorizationRowMapper(rowMapper);
    service.setAuthorizationParametersMapper(
        new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(mapper));
  }
}
