package br.com.vanep.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MobileOAuthClientTest {

  @Autowired private RegisteredClientRepository registeredClients;

  @Test
  void registersPublicMobileClientWithCustomSchemeRedirectAndPkce() {
    assertThat(registeredClients.findByClientId("vanep-mobile"))
        .isNotNull()
        .satisfies(
            mobile -> {
              assertThat(mobile.getClientAuthenticationMethods())
                  .containsExactly(ClientAuthenticationMethod.NONE);
              assertThat(mobile.getClientSettings().isRequireProofKey()).isTrue();
              assertThat(mobile.getRedirectUris())
                  .contains("com.vanep.vanepmobile://oauth2redirect");
              assertThat(mobile.getAuthorizationGrantTypes())
                  .contains(
                      AuthorizationGrantType.AUTHORIZATION_CODE,
                      AuthorizationGrantType.REFRESH_TOKEN);
              assertThat(mobile.getScopes()).contains("read", "write");
            });
  }

  @Test
  void keepsWebClientRegistered() {
    assertThat(registeredClients.findByClientId("vanep-frontend")).isNotNull();
  }
}
