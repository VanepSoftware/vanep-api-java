package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.LocaleResolver;

@SpringBootTest
@ActiveProfiles("test")
class LocaleConfigurationTest {

  private static final Locale BRAZILIAN_PORTUGUESE = Locale.forLanguageTag("pt-BR");

  @Autowired private LocaleResolver localeResolver;

  @Test
  void defaultsToBrazilianPortugueseWithoutAcceptLanguage() {
    var request = new MockHttpServletRequest();

    assertThat(localeResolver.resolveLocale(request)).isEqualTo(BRAZILIAN_PORTUGUESE);
  }

  @Test
  void keepsBrazilianPortugueseWhenAcceptLanguageRequestsAnotherLocale() {
    var request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "en-US");

    assertThat(localeResolver.resolveLocale(request)).isEqualTo(BRAZILIAN_PORTUGUESE);
  }
}
