package br.com.vanep.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class GoogleOAuth2Config {

  @Bean
  @ConditionalOnExpression(
      "T(org.springframework.util.StringUtils).hasText('${GOOGLE_CLIENT_ID:}')")
  public ClientRegistrationRepository googleClientRegistrationRepository(
      @Value("${GOOGLE_CLIENT_ID}") String clientId,
      @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret) {
    return new InMemoryClientRegistrationRepository(
        CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope("openid", "email", "profile")
            .build());
  }
}
