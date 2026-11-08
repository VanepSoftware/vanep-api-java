package br.com.vanep.auth.oauth.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class MobileClientAuthenticationConverterTest {

  private final MobileClientAuthenticationConverter converter =
      new MobileClientAuthenticationConverter(AuthorizationServerSettings.builder().build());

  @Test
  void tokenRequestWithoutGrantTypeIsIgnoredInsteadOfThrowing() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
    request.setParameter("client_id", "vanep-mobile");

    assertThatCode(() -> converter.convert(request)).doesNotThrowAnyException();
    assertThat(converter.convert(request)).isNull();
  }

  @Test
  void tokenRequestWithSupportedGrantTypeIsConverted() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
    request.setParameter("client_id", "vanep-mobile");
    request.setParameter("grant_type", "refresh_token");

    assertThat(converter.convert(request)).isNotNull();
  }
}
